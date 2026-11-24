package com.westmouthbay.fedleave.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeveloperFixture {

    suspend fun importNamedAssets(
        context: Context,
        coordinator: StoreCoordinator,
        assetNames: List<String>,
        assetPrefix: String = "",
    ): PublishResult = withContext(Dispatchers.IO) {
        val candidate = StoreCandidate()
        for (name in assetNames) {
            val bytes = context.assets.open(name).use { it.readBytes() }
            val canonicalName = name.removePrefix(assetPrefix)
            candidate.put(canonicalName, StoreValidator.decodeUtf8Strict(bytes))
        }
        coordinator.publish(candidate, expectedBase = null, fingerprint = "developer-fixture")
    }
}
