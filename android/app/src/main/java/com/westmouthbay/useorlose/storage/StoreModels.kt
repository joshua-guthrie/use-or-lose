package com.westmouthbay.useorlose.storage

data class StorageSnapshot(
    val generationId: String,
    val files: Map<String, String>,
)

data class GenerationRef(
    val generationId: String,
    val committedAt: String?,
)

sealed interface OpenResult {
    data class Ready(val snapshot: StorageSnapshot) : OpenResult
    data class Recoverable(val choices: List<GenerationRef>) : OpenResult
    data object Empty : OpenResult
}

data class ValidationIssue(
    val code: String,
    val path: String,
    val message: String,
)

sealed interface PublishResult {
    data class Published(val generationId: String) : PublishResult
    data class Rejected(val issues: List<ValidationIssue>) : PublishResult
    data object StaleBase : PublishResult
    data class Failed(val reason: String) : PublishResult
}
