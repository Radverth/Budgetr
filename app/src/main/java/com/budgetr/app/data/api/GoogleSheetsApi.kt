package com.budgetr.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleSheetsApi {

    @GET("spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("fields") fields: String = "sheets.properties"
    ): SpreadsheetMetadata

    @GET("spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("key") apiKey: String? = null
    ): ValueRange

    @POST("spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: ValueRange
    ): AppendResponse

    @PUT("spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: ValueRange
    ): UpdateResponse

    @POST("spreadsheets/{spreadsheetId}/values:batchUpdate")
    suspend fun batchUpdateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body body: BatchUpdateValuesRequest
    ): Unit

    @POST("spreadsheets/{spreadsheetId}:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body body: BatchUpdateRequest
    ): BatchUpdateResponse

    @POST("spreadsheets")
    suspend fun createSpreadsheet(
        @Body body: CreateSpreadsheetRequest
    ): CreatedSpreadsheet
}

@JsonClass(generateAdapter = true)
data class BatchUpdateValuesRequest(
    @Json(name = "data") val data: List<ValueRange>,
    @Json(name = "valueInputOption") val valueInputOption: String = "RAW"
)

@JsonClass(generateAdapter = true)
data class ValueRange(
    @Json(name = "range") val range: String? = null,
    @Json(name = "majorDimension") val majorDimension: String? = "ROWS",
    @Json(name = "values") val values: List<List<String>>? = null
)

@JsonClass(generateAdapter = true)
data class AppendResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String? = null,
    @Json(name = "updates") val updates: UpdatedRange? = null
)

@JsonClass(generateAdapter = true)
data class UpdateResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String? = null,
    @Json(name = "updatedRange") val updatedRange: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdatedRange(
    @Json(name = "updatedRange") val updatedRange: String? = null,
    @Json(name = "updatedRows") val updatedRows: Int? = null
)

@JsonClass(generateAdapter = true)
data class BatchUpdateRequest(
    @Json(name = "requests") val requests: List<Request>
)

@JsonClass(generateAdapter = true)
data class Request(
    @Json(name = "deleteDimension") val deleteDimension: DeleteDimensionRequest? = null,
    @Json(name = "addSheet") val addSheet: AddSheetRequestBody? = null,
    @Json(name = "updateSheetProperties") val updateSheetProperties: UpdateSheetPropertiesRequest? = null,
    @Json(name = "deleteSheet") val deleteSheet: DeleteSheetRequest? = null
)

@JsonClass(generateAdapter = true)
data class DeleteSheetRequest(
    @Json(name = "sheetId") val sheetId: Int
)

@JsonClass(generateAdapter = true)
data class AddSheetRequestBody(
    @Json(name = "properties") val properties: NewSheetProperties
)

@JsonClass(generateAdapter = true)
data class NewSheetProperties(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class UpdateSheetPropertiesRequest(
    @Json(name = "properties") val properties: UpdateSheetProps,
    @Json(name = "fields") val fields: String = "title"
)

@JsonClass(generateAdapter = true)
data class UpdateSheetProps(
    @Json(name = "sheetId") val sheetId: Int,
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetRequest(
    @Json(name = "properties") val properties: SpreadsheetTitleProperties,
    @Json(name = "sheets") val sheets: List<SheetSpec>? = null
)

@JsonClass(generateAdapter = true)
data class SpreadsheetTitleProperties(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class SheetSpec(
    @Json(name = "properties") val properties: NewSheetProperties
)

@JsonClass(generateAdapter = true)
data class CreatedSpreadsheet(
    @Json(name = "spreadsheetId") val spreadsheetId: String,
    @Json(name = "spreadsheetUrl") val spreadsheetUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteDimensionRequest(
    @Json(name = "range") val range: DimensionRange
)

@JsonClass(generateAdapter = true)
data class DimensionRange(
    @Json(name = "sheetId") val sheetId: Int,
    @Json(name = "dimension") val dimension: String = "ROWS",
    @Json(name = "startIndex") val startIndex: Int,
    @Json(name = "endIndex") val endIndex: Int
)

@JsonClass(generateAdapter = true)
data class BatchUpdateResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String? = null
)

@JsonClass(generateAdapter = true)
data class SpreadsheetMetadata(
    @Json(name = "sheets") val sheets: List<SheetMetadata>? = null
)

@JsonClass(generateAdapter = true)
data class SheetMetadata(
    @Json(name = "properties") val properties: SheetProperties? = null
)

@JsonClass(generateAdapter = true)
data class SheetProperties(
    @Json(name = "sheetId") val sheetId: Int,
    @Json(name = "title") val title: String
)
