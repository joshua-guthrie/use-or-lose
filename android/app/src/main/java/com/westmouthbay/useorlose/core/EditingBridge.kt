package com.westmouthbay.useorlose.core

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * The typed write-side day-editing surface over the bridge module
 * `useorlose_editing`, which mirrors the CLI writes (set-day group
 * replacement and add) over the shared pure core. Returns candidate files for
 * the I04 store publisher plus a preview. All calls run off the main thread
 * and map failures to [CoreError].
 */

data class EditDayRequest(
    val year: Int,
    val date: LocalDate,
    val category: String,
    val direction: String,
    val hours: Double,
    val comment: String?,
    val ledgerJson: String,
)

data class AddEntryRequest(
    val year: Int,
    val date: LocalDate,
    val category: String,
    val direction: String,
    val hours: Double,
    val comment: String,
    val status: String,
    val ledgerJson: String,
)

data class EditDayPreview(
    val year: Int,
    val date: LocalDate,
    val category: String,
    val direction: String,
    val beforeHours: Double,
    val afterHours: Double,
    val removedTransactionIds: List<String>,
    val createdTransactionIds: List<String>,
    val comment: String,
    val protectedRows: List<String>,
)

data class AddEntryPreview(
    val year: Int,
    val date: LocalDate,
    val category: String,
    val direction: String,
    val hours: Double,
    val comment: String,
    val status: String,
    val createdTransactionIds: List<String>,
)

/** Individual rows that cannot be consolidated (routes to I09 correction). */
data class MixedMetadataRow(
    val id: String?,
    val category: String,
    val direction: String?,
    val hours: Double,
    val status: String?,
    val source: String?,
    val description: String,
)

data class MixedMetadataPreview(
    val year: Int,
    val date: LocalDate,
    val category: String,
    val direction: String,
    val rows: List<MixedMetadataRow>,
)

sealed interface EditPreview {
    data class Day(val value: EditDayPreview) : EditPreview
    data class Add(val value: AddEntryPreview) : EditPreview
    data class Mixed(val value: MixedMetadataPreview) : EditPreview
}

data class EditResult(
    val files: Map<String, String>,
    val preview: EditPreview,
)

/** The editing surface the day editor consumes (fakes in tests). */
interface EditingRead {
    suspend fun previewEditDay(request: EditDayRequest): EditResult
    suspend fun previewAddEntry(request: AddEntryRequest): EditResult
}

class EditingBridge : EditingRead {

    override suspend fun previewEditDay(request: EditDayRequest): EditResult =
        withContext(Dispatchers.IO) {
            val envelope = invoke(
                JSONObject()
                    .put("operation", "edit_day")
                    .put("year", request.year)
                    .put("date", request.date.toString())
                    .put("category", request.category)
                    .put("direction", request.direction)
                    .put("hours", request.hours)
                    .put("comment", request.comment ?: JSONObject.NULL)
                    .put("ledger", request.ledgerJson),
            )
            parseResult(envelope)
        }

    override suspend fun previewAddEntry(request: AddEntryRequest): EditResult =
        withContext(Dispatchers.IO) {
            val envelope = invoke(
                JSONObject()
                    .put("operation", "add_entry")
                    .put("year", request.year)
                    .put("date", request.date.toString())
                    .put("category", request.category)
                    .put("direction", request.direction)
                    .put("hours", request.hours)
                    .put("comment", request.comment)
                    .put("status", request.status)
                    .put("ledger", request.ledgerJson),
            )
            parseResult(envelope)
        }

    private suspend fun invoke(request: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject(request.toString()).put("schema_version", 1)
        val raw = try {
            Python.getInstance().getModule("useorlose_editing").callAttr("edit", payload.toString()).toString()
        } catch (error: Throwable) {
            throw CoreError.Unexpected("Editing call failed: ${error.message}", error)
        }
        val envelope = try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw CoreError.Unexpected("Editing bridge returned non-JSON text.", error)
        }
        if (!envelope.optBoolean("ok")) {
            throw CoreError.fromErrorObject(envelope.optJSONObject("error") ?: JSONObject())
        }
        envelope
    }

    private fun parseResult(envelope: JSONObject): EditResult {
        val result = envelope.optJSONObject("result")
            ?: throw CoreError.Unexpected("Editing bridge returned ok=true without a result.")
        val files = mutableMapOf<String, String>()
        val filesObject = result.optJSONObject("files") ?: JSONObject()
        for (key in filesObject.keys()) {
            files[key] = filesObject.getString(key)
        }
        val previewObject = result.optJSONObject("preview")
        val preview = when (previewObject?.optString("kind")) {
            "edit_day" -> EditPreview.Day(
                EditDayPreview(
                    year = previewObject.getInt("year"),
                    date = LocalDate.parse(previewObject.getString("date")),
                    category = previewObject.getString("category"),
                    direction = previewObject.getString("direction"),
                    beforeHours = previewObject.getDouble("before_hours"),
                    afterHours = previewObject.getDouble("after_hours"),
                    removedTransactionIds = previewObject.optJSONArray("removed_transaction_ids")
                        ?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
                    createdTransactionIds = previewObject.optJSONArray("created_transaction_ids")
                        ?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
                    comment = previewObject.optString("comment", ""),
                    protectedRows = previewObject.optJSONArray("protected_rows")
                        ?.let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("id") } }
                        ?: emptyList(),
                ),
            )
            "add_entry" -> EditPreview.Add(
                AddEntryPreview(
                    year = previewObject.getInt("year"),
                    date = LocalDate.parse(previewObject.getString("date")),
                    category = previewObject.getString("category"),
                    direction = previewObject.getString("direction"),
                    hours = previewObject.getDouble("hours"),
                    comment = previewObject.optString("comment", ""),
                    status = previewObject.optString("status", "planned"),
                    createdTransactionIds = previewObject.optJSONArray("created_transaction_ids")
                        ?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
                ),
            )
            "mixed_metadata" -> EditPreview.Mixed(
                MixedMetadataPreview(
                    year = previewObject.getInt("year"),
                    date = LocalDate.parse(previewObject.getString("date")),
                    category = previewObject.getString("category"),
                    direction = previewObject.getString("direction"),
                    rows = previewObject.optJSONArray("rows")?.let { array ->
                        (0 until array.length()).map { index ->
                            val row = array.getJSONObject(index)
                            MixedMetadataRow(
                                id = row.nullableString("id"),
                                category = row.getString("category"),
                                direction = row.nullableString("direction"),
                                hours = row.getDouble("hours"),
                                status = row.nullableString("status"),
                                source = row.nullableString("source"),
                                description = row.optString("description", ""),
                            )
                        }
                    } ?: emptyList(),
                ),
            )
            else -> throw CoreError.Unexpected("Editing bridge returned an unknown preview kind.")
        }
        return EditResult(files = files, preview = preview)
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
