package com.westmouthbay.useorlose.storage

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

object StoreValidator {

    fun validate(files: Map<String, String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        val configText = files[StorePaths.CONFIG]
        if (configText == null) {
            issues += ValidationIssue("MISSING_CONFIG", StorePaths.CONFIG, "config.json is required.")
        } else {
            val config = parseObject(configText, StorePaths.CONFIG)
            if (config == null) {
                issues += ValidationIssue("INVALID_JSON", StorePaths.CONFIG, "config.json must be a JSON object.")
            } else {
                checkSchema(config, StorePaths.CONFIG, issues)
            }
        }

        val yearFiles = files.entries.filter { it.key.startsWith("leave_years/") }
        if (yearFiles.isEmpty()) {
            issues += ValidationIssue("MISSING_LEDGER", "leave_years", "At least one leave year is required.")
        }
        for ((name, text) in yearFiles) {
            val year = StorePaths.yearOf(name)
            val ledger = parseObject(text, name)
            if (ledger == null) {
                issues += ValidationIssue("INVALID_JSON", name, "$name must be a JSON object.")
                continue
            }
            checkSchema(ledger, name, issues)
            val declaredYear = ledger.optInt("leave_year", -1)
            if (year == null || declaredYear != year) {
                issues +=
                    ValidationIssue("YEAR_MISMATCH", name, "leave_year must match the filename ${name.substringAfterLast('/')}.")
            }
            validateTransactions(ledger, name, issues)
            validateDates(ledger, name, issues)
            validateBalances(ledger, name, issues)
            validatePayPeriods(ledger, name, issues)
        }

        for ((name, text) in files.entries.filter { it.key.startsWith("holiday_cache/") }) {
            val cache = parseObject(text, name)
            if (cache == null) {
                issues += ValidationIssue("INVALID_JSON", name, "$name must be a JSON object.")
                continue
            }
            checkSchema(cache, name, issues)
            if (!cache.has("holidays") || cache.optJSONArray("holidays") == null) {
                issues += ValidationIssue("INVALID_CACHE", name, "$name must contain a holidays list.")
            }
        }

        return issues
    }

    private fun parseObject(text: String, path: String): JSONObject? = try {
        val value = JSONObject(text)
        value
    } catch (error: org.json.JSONException) {
        null
    }

    private fun checkSchema(value: JSONObject, path: String, issues: MutableList<ValidationIssue>) {
        if (value.optInt("schema_version", 1) != 1) {
            issues += ValidationIssue("UNSUPPORTED_SCHEMA", path, "schema_version must be 1.")
        }
    }

    private fun validateTransactions(
        ledger: JSONObject,
        path: String,
        issues: MutableList<ValidationIssue>,
    ) {
        val transactions = ledger.optJSONArray("transactions")
        if (transactions == null) {
            issues += ValidationIssue("INVALID_TRANSACTIONS", path, "transactions must be a list.")
            return
        }
        val seen = mutableSetOf<String>()
        for (index in 0 until transactions.length()) {
            val row = transactions.optJSONObject(index)
            if (row == null) {
                issues += ValidationIssue("INVALID_TRANSACTION", "$path.transactions[$index]", "Transactions must be objects.")
                continue
            }
            val id = row.optString("id", "")
            if (id.isNotEmpty() && !seen.add(id)) {
                issues += ValidationIssue("DUPLICATE_ID", "$path.transactions[$index]", "Duplicate transaction id $id.")
            }
            if (row.has("hours") && !isFiniteNumber(row.opt("hours"))) {
                issues += ValidationIssue("INVALID_HOURS", "$path.transactions[$index].hours", "hours must be a finite number.")
            }
            if (row.has("date") && !isDate(row.optString("date", ""))) {
                issues += ValidationIssue("INVALID_DATE", "$path.transactions[$index].date", "date must be an ISO date.")
            }
        }
    }

    private fun validateDates(ledger: JSONObject, path: String, issues: MutableList<ValidationIssue>) {
        val start = ledger.optString("leave_year_start", "")
        val end = ledger.optString("leave_year_end", "")
        if (!isDate(start)) {
            issues += ValidationIssue("INVALID_DATE", "$path.leave_year_start", "leave_year_start must be an ISO date.")
        }
        if (!isDate(end)) {
            issues += ValidationIssue("INVALID_DATE", "$path.leave_year_end", "leave_year_end must be an ISO date.")
        }
        if (isDate(start) && isDate(end) && LocalDate.parse(start) > LocalDate.parse(end)) {
            issues += ValidationIssue("INVALID_RANGE", path, "leave_year_start must not be after leave_year_end.")
        }
    }

    private fun validateBalances(ledger: JSONObject, path: String, issues: MutableList<ValidationIssue>) {
        for (field in listOf("starting_balances", "carryover_from_previous_year")) {
            val balances = ledger.optJSONObject(field)
            if (balances == null) {
                issues += ValidationIssue("INVALID_BALANCES", "$path.$field", "$field must be an object.")
                continue
            }
            for (key in balances.keys()) {
                if (!isFiniteNumber(balances.opt(key))) {
                    issues += ValidationIssue("INVALID_HOURS", "$path.$field.$key", "$field.$key must be a finite number.")
                }
            }
        }
    }

    private fun validatePayPeriods(ledger: JSONObject, path: String, issues: MutableList<ValidationIssue>) {
        val periods = ledger.optJSONArray("pay_periods")
        if (periods == null) {
            issues += ValidationIssue("INVALID_PAY_PERIODS", path, "pay_periods must be a list.")
            return
        }
        for (index in 0 until periods.length()) {
            val period = periods.optJSONObject(index)
            if (period == null) {
                issues += ValidationIssue("INVALID_PAY_PERIOD", "$path.pay_periods[$index]", "Pay periods must be objects.")
                continue
            }
            val start = period.optString("start_date", "")
            val end = period.optString("end_date", "")
            if (!isDate(start) || !isDate(end)) {
                issues += ValidationIssue("INVALID_DATE", "$path.pay_periods[$index]", "Pay-period bounds must be ISO dates.")
            } else if (LocalDate.parse(start) > LocalDate.parse(end)) {
                issues += ValidationIssue("INVALID_RANGE", "$path.pay_periods[$index]", "Pay-period start must not be after its end.")
            }
            for (field in listOf("pay_date", "accrual_date")) {
                val value = period.optString(field, "")
                if (value.isNotEmpty() && !isDate(value)) {
                    issues += ValidationIssue("INVALID_DATE", "$path.pay_periods[$index].$field", "$field must be an ISO date.")
                }
            }
        }
    }

    private fun isFiniteNumber(value: Any?): Boolean = when (value) {
        is Int, is Long, is Short, is Byte, is Double, is Float -> (value as Number).toDouble().isFinite()
        is java.math.BigDecimal -> value.toDouble().isFinite()
        is java.math.BigInteger -> value.toDouble().isFinite()
        else -> false
    }

    private fun isDate(value: String): Boolean = try {
        LocalDate.parse(value).toString() == value
    } catch (error: DateTimeParseException) {
        false
    }

    fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    fun validHolidayRows(text: String): Boolean {
        val cache = parseObject(text, "holiday_cache") ?: return false
        val rows = cache.optJSONArray("holidays") ?: return false
        for (index in 0 until rows.length()) {
            if (rows.optJSONObject(index) == null) return false
        }
        return true
    }

    fun validConfigShape(text: String): Boolean {
        val config = parseObject(text, StorePaths.CONFIG) ?: return false
        return config.optInt("schema_version", 1) == 1
    }

    fun validLedgerShape(text: String, year: Int): Boolean {
        val ledger = parseObject(text, "leave_years/$year.json") ?: return false
        return ledger.optInt("schema_version", 1) == 1 && ledger.optInt("leave_year", -1) == year
    }
}
