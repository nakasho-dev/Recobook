package org.ukky.recobook.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BooksApi(
    private val client: HttpClient,
) {
    suspend fun fetchByIsbn(isbn: String): Book? {
        val response: List<OpenBdBookResponse?> = client
            .get("https://api.openbd.jp/v1/get") {
                parameter("isbn", isbn)
            }
            .body()
        val item = response.firstOrNull() ?: return null
        return item.toBook(isbn)
    }
}

private fun OpenBdBookResponse.toBook(fallbackIsbn: String): Book {
    val isbn13 = firstNonBlank(
        summary?.isbn.takeUnlessBlank()?.takeIf { it.length == 13 },
        onix?.productIdentifier?.idValue.takeUnlessBlank()?.takeIf { it.length == 13 },
        onix?.recordReference.takeUnlessBlank()?.takeIf { it.length == 13 },
    )
    val isbn10 = fallbackIsbn.takeIf { it.length == 10 }
        ?: summary?.isbn.takeUnlessBlank()?.takeIf { it.length == 10 }
        ?: isbn13?.toIsbn10OrNull()
    val canonicalIsbn = isbn13 ?: isbn10 ?: fallbackIsbn
    val title = firstNonBlank(
        summary?.title.takeUnlessBlank(),
        onix?.descriptiveDetail?.titleDetail?.titleElement?.titleText?.content.takeUnlessBlank(),
        canonicalIsbn,
    ) ?: canonicalIsbn
    val authorsFromOnix = onix?.descriptiveDetail?.contributors
        .orEmpty()
        .mapNotNull { it.personName?.content.takeUnlessBlank() }
    val authors = if (authorsFromOnix.isNotEmpty()) {
        authorsFromOnix
    } else {
        summary?.author.takeUnlessBlank()?.let(::listOf).orEmpty()
    }
    val publisher = firstNonBlank(
        summary?.publisher.takeUnlessBlank(),
        onix?.publishingDetail?.imprint?.imprintName.takeUnlessBlank(),
        onix?.publishingDetail?.publisher?.publisherName.takeUnlessBlank(),
    )
    val publishedDate = formatOpenBdDate(
        firstNonBlank(
            summary?.pubdate.takeUnlessBlank(),
            onix?.publishingDetail?.preferredPublishingDate()?.date.takeUnlessBlank(),
        ),
    )
    val description = onix?.collateralDetail?.preferredTextContent()?.text.takeUnlessBlank()
    val thumbnailUrl = firstNonBlank(
        summary?.cover.takeUnlessBlank(),
        onix?.collateralDetail?.firstResourceLink(),
    )?.replace("http://", "https://")
    val pageCount = onix?.descriptiveDetail?.firstPageCount()
    val categories = onix?.descriptiveDetail?.subjects
        .orEmpty()
        .mapNotNull { it.subjectCode.takeUnlessBlank() }

    return Book(
        id = canonicalIsbn,
        isbn = canonicalIsbn,
        isbn10 = isbn10,
        isbn13 = isbn13,
        title = title,
        authors = authors,
        publisher = publisher,
        publishedDate = publishedDate,
        description = description,
        thumbnailUrl = thumbnailUrl,
        pageCount = pageCount,
        categories = categories,
    )
}

private fun OpenBdPublishingDetail.preferredPublishingDate(): OpenBdPublishingDate? {
    val dates = publishingDates.orEmpty()
    return dates.firstOrNull { it.role == "01" }
        ?: dates.firstOrNull { it.role == "11" }
        ?: dates.firstOrNull { it.role == "25" }
        ?: dates.firstOrNull { it.role == "09" }
        ?: dates.firstOrNull { it.date.takeUnlessBlank() != null }
}

private fun OpenBdCollateralDetail.preferredTextContent(): OpenBdTextContent? {
    val items = textContents.orEmpty()
    return items.firstOrNull { it.type == "03" && it.text.takeUnlessBlank() != null }
        ?: items.firstOrNull { it.type == "02" && it.text.takeUnlessBlank() != null }
        ?: items.firstOrNull { it.type == "04" && it.text.takeUnlessBlank() != null }
        ?: items.firstOrNull { it.text.takeUnlessBlank() != null }
}

private fun OpenBdCollateralDetail.firstResourceLink(): String? {
    return supportingResources
        .orEmpty()
        .asSequence()
        .flatMap { it.resourceVersions.orEmpty().asSequence() }
        .mapNotNull { it.resourceLink.takeUnlessBlank() }
        .firstOrNull()
}

private fun OpenBdDescriptiveDetail.firstPageCount(): Int? {
    val extents = extents.orEmpty()
    return extents.firstNotNullOfOrNull {
        if ((it.type == null || it.type == "11") && (it.unit == null || it.unit == "03")) {
            it.value?.toIntOrNull()
        } else {
            null
        }
    } ?: extents.firstNotNullOfOrNull { it.value?.toIntOrNull() }
}

private fun formatOpenBdDate(value: String?): String? {
    val raw = value.takeUnlessBlank() ?: return null
    return when {
        raw.length == 8 && raw.all(Char::isDigit) -> "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        raw.length == 6 && raw.all(Char::isDigit) -> "${raw.substring(0, 4)}-${raw.substring(4, 6)}"
        raw.length == 4 && raw.all(Char::isDigit) -> raw
        else -> raw
    }
}

private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

private fun String?.takeUnlessBlank(): String? = this?.takeUnless { it.isBlank() }

private fun String.toIsbn10OrNull(): String? {
    if (length != 13 || !startsWith("978") || any { !it.isDigit() }) return null
    val body = substring(3, 12)
    val sum = body.mapIndexed { index, char -> (10 - index) * (char.digitToInt()) }.sum()
    val checkDigit = (11 - (sum % 11)) % 11
    val suffix = when (checkDigit) {
        10 -> "X"
        else -> checkDigit.toString()
    }
    return body + suffix
}

@Serializable
private data class OpenBdBookResponse(
    val onix: OpenBdOnix? = null,
    val summary: OpenBdSummary? = null,
)

@Serializable
private data class OpenBdOnix(
    @SerialName("RecordReference") val recordReference: String? = null,
    @SerialName("ProductIdentifier") val productIdentifier: OpenBdProductIdentifier? = null,
    @SerialName("DescriptiveDetail") val descriptiveDetail: OpenBdDescriptiveDetail? = null,
    @SerialName("CollateralDetail") val collateralDetail: OpenBdCollateralDetail? = null,
    @SerialName("PublishingDetail") val publishingDetail: OpenBdPublishingDetail? = null,
)

@Serializable
private data class OpenBdProductIdentifier(
    @SerialName("IDValue") val idValue: String? = null,
)

@Serializable
private data class OpenBdDescriptiveDetail(
    @SerialName("TitleDetail") val titleDetail: OpenBdTitleDetail? = null,
    @SerialName("Contributor") val contributors: List<OpenBdContributor>? = null,
    @SerialName("Extent") val extents: List<OpenBdExtent>? = null,
    @SerialName("Subject") val subjects: List<OpenBdSubject>? = null,
)

@Serializable
private data class OpenBdTitleDetail(
    @SerialName("TitleElement") val titleElement: OpenBdTitleElement? = null,
)

@Serializable
private data class OpenBdTitleElement(
    @SerialName("TitleText") val titleText: OpenBdTextValue? = null,
)

@Serializable
private data class OpenBdTextValue(
    val content: String? = null,
)

@Serializable
private data class OpenBdContributor(
    @SerialName("PersonName") val personName: OpenBdTextValue? = null,
)

@Serializable
private data class OpenBdExtent(
    @SerialName("ExtentType") val type: String? = null,
    @SerialName("ExtentValue") val value: String? = null,
    @SerialName("ExtentUnit") val unit: String? = null,
)

@Serializable
private data class OpenBdSubject(
    @SerialName("SubjectCode") val subjectCode: String? = null,
)

@Serializable
private data class OpenBdCollateralDetail(
    @SerialName("TextContent") val textContents: List<OpenBdTextContent>? = null,
    @SerialName("SupportingResource") val supportingResources: List<OpenBdSupportingResource>? = null,
)

@Serializable
private data class OpenBdTextContent(
    @SerialName("TextType") val type: String? = null,
    @SerialName("Text") val text: String? = null,
)

@Serializable
private data class OpenBdSupportingResource(
    @SerialName("ResourceVersion") val resourceVersions: List<OpenBdResourceVersion>? = null,
)

@Serializable
private data class OpenBdResourceVersion(
    @SerialName("ResourceLink") val resourceLink: String? = null,
)

@Serializable
private data class OpenBdPublishingDetail(
    @SerialName("Imprint") val imprint: OpenBdImprint? = null,
    @SerialName("Publisher") val publisher: OpenBdPublisher? = null,
    @SerialName("PublishingDate") val publishingDates: List<OpenBdPublishingDate>? = null,
)

@Serializable
private data class OpenBdImprint(
    @SerialName("ImprintName") val imprintName: String? = null,
)

@Serializable
private data class OpenBdPublisher(
    @SerialName("PublisherName") val publisherName: String? = null,
)

@Serializable
private data class OpenBdPublishingDate(
    @SerialName("PublishingDateRole") val role: String? = null,
    @SerialName("Date") val date: String? = null,
)

@Serializable
private data class OpenBdSummary(
    val isbn: String? = null,
    val title: String? = null,
    val publisher: String? = null,
    val pubdate: String? = null,
    val cover: String? = null,
    val author: String? = null,
)
