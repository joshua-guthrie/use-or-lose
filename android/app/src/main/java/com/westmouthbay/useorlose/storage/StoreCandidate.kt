package com.westmouthbay.useorlose.storage

class StoreCandidate(initial: Map<String, String> = emptyMap()) {

    private val files: MutableMap<String, String> = LinkedHashMap(initial)

    fun put(relativePath: String, jsonText: String): StoreCandidate {
        require(StorePaths.isCanonical(relativePath)) { "Not a canonical store path: $relativePath" }
        files[relativePath] = jsonText
        return this
    }

    fun remove(relativePath: String) {
        files.remove(relativePath)
    }

    fun files(): Map<String, String> = files.toMap()
}
