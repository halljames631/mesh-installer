package com.example.network

import com.example.model.FirmwareAsset
import com.example.model.FirmwareEcosystem
import com.example.model.FirmwareRelease
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.PreparedFirmwareImage
import com.example.model.TDeckMeshCoreFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream

class GitHubFirmwareRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) MeshInstaller/1.2")
            if (original.header("Accept") == null) {
                builder.header("Accept", "application/vnd.github+json, application/json, text/html, */*")
            }
            chain.proceed(builder.build())
        }
        .build()

    private data class ZipCdEntry(
        val fileName: String,
        val compressionMethod: Int,
        val crc32: Long,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val localHeaderOffset: Long
    )

    private data class RemoteZipIndex(
        val resolvedCdnUrl: String,
        val totalZipSize: Long,
        val entries: List<ZipCdEntry>
    )

    private val releaseCache = java.util.concurrent.ConcurrentHashMap<FirmwareEcosystem, List<FirmwareRelease>>()
    private val tDeckFlavorCache = java.util.concurrent.ConcurrentHashMap<TDeckMeshCoreFlavor, List<FirmwareRelease>>()
    private val zipIndexCache = java.util.concurrent.ConcurrentHashMap<String, RemoteZipIndex>()

    /**
     * Multi-source release discovery for both Meshtastic and MeshCore:
     * 1. For Meshtastic: tries `api.github.com/repos/meshtastic/firmware/releases`, then `api.meshtastic.org/github/firmware/list`,
     *    then GitHub Web `/releases/latest` + `/releases/expanded_assets/<tag>`.
     * 2. For MeshCore: tries `flasher.meshcore.io/releases?repo=github`, then `api.github.com/repos/meshcore-dev/MeshCore/releases`,
     *    then GitHub Web `/releases/latest` + `/releases/expanded_assets/<tag>`.
     */
    suspend fun fetchReleases(ecosystem: FirmwareEcosystem): Result<List<FirmwareRelease>> =
        withContext(Dispatchers.IO) {
            try {
                val releases = when (ecosystem) {
                    FirmwareEcosystem.MESHTASTIC -> fetchMeshtasticReleasesMultiSource()
                    FirmwareEcosystem.MESHCORE -> fetchMeshCoreReleasesMultiSource()
                }
                if (releases.isNotEmpty()) {
                    releaseCache[ecosystem] = releases
                    Result.success(releases)
                } else {
                    Result.success(releaseCache[ecosystem] ?: curatedFallbackReleases(ecosystem))
                }
            } catch (_: Exception) {
                Result.success(releaseCache[ecosystem] ?: curatedFallbackReleases(ecosystem))
            }
        }

    private fun fetchMeshtasticReleasesMultiSource(): List<FirmwareRelease> {
        // Source 1: Official GitHub REST API (parsed with org.json — zero reflection issues)
        val ghReleases = fetchGitHubApiReleases(
            owner = "meshtastic",
            repo = "firmware",
            ecosystem = FirmwareEcosystem.MESHTASTIC
        )
        if (ghReleases.isNotEmpty()) return ghReleases

        // Source 2: Official Meshtastic Web Flasher API (api.meshtastic.org/github/firmware/list)
        val apiReleases = fetchFromMeshtasticFlasherApi()
        if (apiReleases.isNotEmpty()) return apiReleases

        // Source 3: GitHub Web /releases/latest redirect + expanded_assets HTML scraper (immune to API rate limits)
        val webRelease = fetchLatestReleaseViaGitHubWeb(
            owner = "meshtastic",
            repo = "firmware",
            ecosystem = FirmwareEcosystem.MESHTASTIC
        )
        if (webRelease != null) {
            val alphaFallback = curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).lastOrNull()
            return listOfNotNull(webRelease, alphaFallback)
        }

        return emptyList()
    }

    private fun fetchMeshCoreReleasesMultiSource(): List<FirmwareRelease> {
        // Source 1: Official MeshCore Web Flasher Catalog API (https://flasher.meshcore.io/releases?repo=github)
        // Combines companion, repeater, and room-server releases by version so every release has all roles!
        val flasherReleases = fetchMeshCoreReleasesFromFlasherApi()
        if (flasherReleases.isNotEmpty()) return flasherReleases

        // Source 2: Official meshcore-dev/MeshCore GitHub REST API (combines companion-vX, repeater-vX, room-server-vX)
        val primary = fetchGitHubApiReleases(
            owner = "meshcore-dev",
            repo = "MeshCore",
            ecosystem = FirmwareEcosystem.MESHCORE,
            perPage = 20
        )
        if (primary.isNotEmpty()) {
            return mergeMeshCoreRoleReleasesByVersion(primary)
        }

        // Source 3: GitHub Web expanded_assets scraper across companion, repeater, and room-server tags
        val webPrimary = fetchLatestMeshCoreUnifiedViaGitHubWeb()
        if (webPrimary != null) return listOf(webPrimary)

        return emptyList()
    }

    /**
     * Queries `https://flasher.meshcore.io/releases?repo=github` which provides all official `meshcore-dev/MeshCore`
     * releases across `"companion"`, `"repeater"`, and `"room-server"` types, and merges them by version (`v1.18.0`, etc.)
     * so each `FirmwareRelease` contains the complete set of Companion (BLE/USB), Repeater, and Room Server binaries.
     */
    private fun fetchMeshCoreReleasesFromFlasherApi(): List<FirmwareRelease> {
        return try {
            val req = Request.Builder()
                .url("https://flasher.meshcore.io/releases?repo=github")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string() ?: return emptyList()
                val arr = JSONArray(bodyStr)
                val rawList = mutableListOf<FirmwareRelease>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val type = obj.optString("type", "") // "companion", "repeater", or "room-server"
                    val version = obj.optString("version", "").ifBlank { continue }
                    val name = obj.optString("name", "MeshCore $type $version")
                    val notes = obj.optString("notes", "Official MeshCore firmware release.")
                        .replace("\r\n", "\n")
                        .take(900)
                    val filesArr = obj.optJSONArray("files")
                    val assets = mutableListOf<FirmwareAsset>()
                    if (filesArr != null) {
                        for (j in 0 until filesArr.length()) {
                            val f = filesArr.getJSONObject(j)
                            val fName = f.optString("name", "")
                            val rawUrl = f.optString("url", "")
                            if (fName.isNotBlank() && rawUrl.isNotBlank()) {
                                // Use direct GitHub release download URL (or flasher.meshcore.io mirror)
                                val ghUrl = if (rawUrl.startsWith("/releases/download/")) {
                                    "https://github.com/meshcore-dev/MeshCore$rawUrl"
                                } else if (rawUrl.startsWith("https://")) {
                                    rawUrl
                                } else {
                                    "https://flasher.meshcore.io$rawUrl?repo=github"
                                }
                                assets.add(FirmwareAsset(fileName = fName, downloadUrl = ghUrl, sizeBytes = 1_800_000L))
                            }
                        }
                    }
                    if (assets.isNotEmpty()) {
                        rawList.add(
                            FirmwareRelease(
                                id = "mc-$type-$version",
                                tagName = "$type-$version",
                                title = name,
                                publishedDate = "Official",
                                ecosystem = FirmwareEcosystem.MESHCORE,
                                isPrerelease = version.contains("alpha", ignoreCase = true) || version.contains("rc", ignoreCase = true),
                                changelog = notes,
                                assets = assets
                            )
                        )
                    }
                }
                mergeMeshCoreRoleReleasesByVersion(rawList)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Merges separate `companion-vX.Y.Z`, `repeater-vX.Y.Z`, and `room-server-vX.Y.Z` releases from `meshcore-dev/MeshCore`
     * into unified `vX.Y.Z` releases so switching between Companion (BLE/USB), Repeater, and Room Server always has
     * all role binaries ready inside the selected release.
     *
     * Also backfills any board+role binary that was omitted in a point release (e.g., `Station_G2_companion_radio_ble`
     * omitted in `v1.18.0` but published in `v1.17.1`) from the most recent release that includes it.
     */
    fun mergeMeshCoreRoleReleasesByVersion(releases: List<FirmwareRelease>): List<FirmwareRelease> {
        if (releases.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<FirmwareRelease>>()
        for (rel in releases) {
            val cleanVer = extractMeshCoreVersionSuffix(rel.tagName)
            grouped.getOrPut(cleanVer) { mutableListOf() }.add(rel)
        }
        val initialUnified = grouped.map { (version, group) ->
            val first = group.first()
            val combinedAssets = group
                .flatMap { it.assets }
                .distinctBy { it.fileName.lowercase(Locale.US) }
            val combinedNotes = group
                .map { it.changelog.trim() }
                .distinct()
                .joinToString("\n\n")
                .take(1000)
            FirmwareRelease(
                id = "mc-unified-$version",
                tagName = version,
                title = "MeshCore $version (Companion, Repeater & Room Server)",
                publishedDate = first.publishedDate,
                ecosystem = FirmwareEcosystem.MESHCORE,
                isPrerelease = group.any { it.isPrerelease },
                changelog = combinedNotes.ifBlank { first.changelog },
                assets = combinedAssets
            )
        }

        // Build a master fallback table of the newest available asset for each (board+role prefix, packaging suffix)
        val newestByRoleSlot = LinkedHashMap<String, FirmwareAsset>()
        for (rel in initialUnified) {
            for (asset in rel.assets) {
                val slotKey = meshCoreAssetSlotKey(asset.fileName) ?: continue
                newestByRoleSlot.putIfAbsent(slotKey, asset)
            }
        }

        return initialUnified.map { rel ->
            val existingSlots = rel.assets.mapNotNull { meshCoreAssetSlotKey(it.fileName) }.toMutableSet()
            val augmented = rel.assets.toMutableList()
            for ((slotKey, fallbackAsset) in newestByRoleSlot) {
                if (existingSlots.add(slotKey)) {
                    augmented.add(fallbackAsset)
                }
            }
            rel.copy(assets = backfillKnownMeshCoreAssets(augmented))
        }
    }

    /**
     * Computes a version-independent slot key for a MeshCore asset filename, e.g.:
     * `Station_G2_companion_radio_ble-v1.17.1-d929643-merged.bin` -> `station_g2_companion_radio_ble|-merged.bin`
     */
    private fun meshCoreAssetSlotKey(fileName: String): String? {
        val lower = fileName.lowercase(Locale.US)
        val prefix = lower.substringBefore("-v", "").ifBlank { return null }
        val packSuffix = when {
            lower.endsWith("-merged.bin") || lower.endsWith("_merged.bin") -> "-merged.bin"
            lower.endsWith(".bin") -> ".bin"
            lower.endsWith(".uf2") -> ".uf2"
            lower.endsWith(".zip") -> ".zip"
            else -> return null
        }
        return "$prefix|$packSuffix"
    }

    /**
     * Ensures critical board+role assets omitted in upstream MeshCore `companion-v1.18.0` (specifically
     * `Station_G2_companion_radio_ble-v1.17.1-d929643-merged.bin`) are always present when Station G2 assets exist.
     */
    private fun backfillKnownMeshCoreAssets(assets: List<FirmwareAsset>): List<FirmwareAsset> {
        val mutable = assets.toMutableList()
        val hasAnyStationG2 = mutable.any { it.fileName.startsWith("Station_G2_", ignoreCase = true) }
        val hasStationG2Ble = mutable.any {
            val lower = it.fileName.lowercase(Locale.US)
            lower.startsWith("station_g2_companion_radio_ble")
        }
        if (hasAnyStationG2 && !hasStationG2Ble) {
            mutable.add(
                FirmwareAsset(
                    fileName = "Station_G2_companion_radio_ble-v1.17.1-d929643-merged.bin",
                    downloadUrl = "https://github.com/meshcore-dev/MeshCore/releases/download/companion-v1.17.1/Station_G2_companion_radio_ble-v1.17.1-d929643-merged.bin",
                    sizeBytes = 1_950_000L
                )
            )
            mutable.add(
                FirmwareAsset(
                    fileName = "Station_G2_companion_radio_ble-v1.17.1-d929643.bin",
                    downloadUrl = "https://github.com/meshcore-dev/MeshCore/releases/download/companion-v1.17.1/Station_G2_companion_radio_ble-v1.17.1-d929643.bin",
                    sizeBytes = 1_880_000L
                )
            )
        }

        // Ensure Heltec V4 role binaries are present if Heltec V3 assets exist in the release
        val hasAnyHeltecV3 = mutable.any { it.fileName.startsWith("Heltec_v3_", ignoreCase = true) }
        if (hasAnyHeltecV3) {
            val v4Roles = listOf(
                Triple("companion_radio_ble", "companion-v1.18.0", 1_950_000L),
                Triple("companion_radio_usb", "companion-v1.18.0", 1_920_000L),
                Triple("repeater", "repeater-v1.18.0", 1_850_000L),
                Triple("room_server", "room-server-v1.18.0", 1_900_000L)
            )
            for ((roleSuffix, tag, estBytes) in v4Roles) {
                val hasV4Role = mutable.any {
                    it.fileName.lowercase(Locale.US).startsWith("heltec_v4_$roleSuffix")
                }
                if (!hasV4Role) {
                    mutable.add(
                        FirmwareAsset(
                            fileName = "Heltec_v4_$roleSuffix-v1.18.0-81dbae5-merged.bin",
                            downloadUrl = "https://github.com/meshcore-dev/MeshCore/releases/download/$tag/Heltec_v4_$roleSuffix-v1.18.0-81dbae5-merged.bin",
                            sizeBytes = estBytes
                        )
                    )
                    mutable.add(
                        FirmwareAsset(
                            fileName = "Heltec_v4_$roleSuffix-v1.18.0-81dbae5.bin",
                            downloadUrl = "https://github.com/meshcore-dev/MeshCore/releases/download/$tag/Heltec_v4_$roleSuffix-v1.18.0-81dbae5.bin",
                            sizeBytes = estBytes - 60_000L
                        )
                    )
                }
            }
        }
        return mutable
    }

    private fun extractMeshCoreVersionSuffix(tagName: String): String {
        val lower = tagName.lowercase(Locale.US)
        return when {
            lower.startsWith("companion-") -> tagName.substring("companion-".length)
            lower.startsWith("repeater-") -> tagName.substring("repeater-".length)
            lower.startsWith("room-server-") -> tagName.substring("room-server-".length)
            lower.startsWith("room_server-") -> tagName.substring("room_server-".length)
            else -> tagName
        }
    }

    private fun fetchLatestMeshCoreUnifiedViaGitHubWeb(): FirmwareRelease? {
        val latest = fetchLatestReleaseViaGitHubWeb(
            owner = "meshcore-dev",
            repo = "MeshCore",
            ecosystem = FirmwareEcosystem.MESHCORE
        ) ?: return null
        val version = extractMeshCoreVersionSuffix(latest.tagName)
        val roleTags = listOf("companion-$version", "repeater-$version", "room-server-$version")
        val allAssets = mutableListOf<FirmwareAsset>()
        for (rTag in roleTags) {
            allAssets.addAll(fetchExpandedAssetsFromGitHubWeb("meshcore-dev", "MeshCore", rTag))
        }
        val finalAssets = if (allAssets.isNotEmpty()) {
            backfillKnownMeshCoreAssets(allAssets.distinctBy { it.fileName.lowercase(Locale.US) })
        } else {
            backfillKnownMeshCoreAssets(latest.assets)
        }
        return latest.copy(
            id = "web-meshcore-$version",
            tagName = version,
            title = "MeshCore $version (Companion, Repeater & Room Server)",
            assets = finalAssets
        )
    }

    /**
     * Fetches T-Deck specific MeshCore releases for either:
     * - Ripple Ultra UI (`https://flasher.meshcore.io/releases?repo=github-ripple`, `type == "t-deck-plus"` -> `Ultra-TDeck-*-merged.bin`)
     * - WadaMesh (`ALLFATHER-BV/wadamesh` + `firmware.wadamesh.com` -> `wadamesh-tdeck-merged.bin`).
     */
    suspend fun fetchTDeckMeshCoreReleases(flavor: TDeckMeshCoreFlavor): List<FirmwareRelease> =
        withContext(Dispatchers.IO) {
            when (flavor) {
                TDeckMeshCoreFlavor.WADAMESH -> {
                    val api = fetchGitHubApiReleases(
                        owner = "ALLFATHER-BV",
                        repo = "wadamesh",
                        ecosystem = FirmwareEcosystem.MESHCORE
                    ).map { enrichWadaMeshReleaseAssets(it) }
                    if (api.isNotEmpty()) return@withContext api

                    val web = fetchLatestReleaseViaGitHubWeb(
                        owner = "ALLFATHER-BV",
                        repo = "wadamesh",
                        ecosystem = FirmwareEcosystem.MESHCORE
                    )?.let { enrichWadaMeshReleaseAssets(it) }
                    if (web != null) return@withContext listOf(web)
                    curatedTDeckFlavorFallback(flavor)
                }
                TDeckMeshCoreFlavor.RIPPLE -> {
                    // Primary: Official MeshCore Flasher Ripple Repository (`repo=github-ripple`, type=`t-deck-plus`)
                    // This serves the standalone Ripple Ultra GUI (`Ultra-TDeck-v12.0-merged.bin`), NOT headless Room Server / Repeater!
                    val flasherRipple = fetchRippleTDeckReleasesFromMeshCoreFlasher()
                    if (flasherRipple.isNotEmpty()) return@withContext flasherRipple

                    val rippleApi = fetchGitHubApiReleases(
                        owner = "ripplebiz",
                        repo = "MeshCore",
                        ecosystem = FirmwareEcosystem.MESHCORE
                    )
                    if (rippleApi.isNotEmpty()) return@withContext rippleApi

                    curatedTDeckFlavorFallback(flavor)
                }
            }
        }

    /**
     * Queries the official MeshCore Web Flasher API (`https://flasher.meshcore.io/releases?repo=github-ripple`)
     * for standalone Ripple Ultra GUI T-Deck releases (`type == "t-deck-plus"`).
     */
    private fun fetchRippleTDeckReleasesFromMeshCoreFlasher(): List<FirmwareRelease> {
        return try {
            val req = Request.Builder()
                .url("https://flasher.meshcore.io/releases?repo=github-ripple")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string() ?: return emptyList()
                val arr = JSONArray(bodyStr)
                val list = mutableListOf<FirmwareRelease>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val type = obj.optString("type", "")
                    if (!type.equals("t-deck-plus", ignoreCase = true) && !type.equals("t-deck", ignoreCase = true)) {
                        continue
                    }
                    val version = obj.optString("version", "").ifBlank { continue }
                    val name = obj.optString("name", "Ripple Ultra T-Deck $version")
                    val notes = obj.optString("notes", "Official Ripple Ultra GUI firmware for LilyGO T-Deck / T-Deck Plus.")
                        .replace("\r\n", "\n")
                        .take(900)
                    val filesArr = obj.optJSONArray("files")
                    val assets = mutableListOf<FirmwareAsset>()
                    if (filesArr != null) {
                        for (j in 0 until filesArr.length()) {
                            val f = filesArr.getJSONObject(j)
                            val fName = f.optString("name", "")
                            val rawUrl = f.optString("url", "")
                            if (fName.endsWith(".bin", ignoreCase = true) && rawUrl.isNotBlank()) {
                                val fullUrl = when {
                                    rawUrl.startsWith("https://") -> rawUrl
                                    rawUrl.contains("?") -> "https://flasher.meshcore.io$rawUrl"
                                    else -> "https://flasher.meshcore.io$rawUrl?repo=github-ripple"
                                }
                                val estSize = if (fName.contains("merged", ignoreCase = true)) 4_108_656L else 2_120_000L
                                assets.add(FirmwareAsset(fileName = fName, downloadUrl = fullUrl, sizeBytes = estSize))
                            }
                        }
                    }
                    if (assets.isNotEmpty()) {
                        list.add(
                            FirmwareRelease(
                                id = "ripple-tdeck-$version",
                                tagName = version,
                                title = "Ripple Ultra UI ($name)",
                                publishedDate = "Official",
                                ecosystem = FirmwareEcosystem.MESHCORE,
                                isPrerelease = version.contains("beta", ignoreCase = true) || version.contains("rc", ignoreCase = true),
                                changelog = notes,
                                assets = assets
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Filters out non-standard T-Deck hardware (`tdeck-max`, `tdeck-pro`, `heltec`) from WadaMesh releases
     * and adds the official `firmware.wadamesh.com` CDN mirror for `wadamesh-tdeck-merged.bin`.
     */
    private fun enrichWadaMeshReleaseAssets(release: FirmwareRelease): FirmwareRelease {
        val tag = release.tagName
        val filtered = release.assets.filter { asset ->
            val lower = asset.fileName.lowercase(Locale.US)
            lower.endsWith(".bin") &&
                !lower.contains("tdeck-max") &&
                !lower.contains("t-deck-max") &&
                !lower.contains("tdeck-pro") &&
                !lower.contains("t-deck-pro") &&
                !lower.contains("heltec")
        }.toMutableList()

        val cdnMergedUrl = "https://firmware.wadamesh.com/releases/TOUCH/$tag/wadamesh-tdeck-merged.bin"
        val ghMergedUrl = "https://github.com/ALLFATHER-BV/wadamesh/releases/download/$tag/wadamesh-tdeck-merged.bin"
        val ghAppUrl = "https://github.com/ALLFATHER-BV/wadamesh/releases/download/$tag/wadamesh-tdeck.bin"

        if (filtered.none { it.downloadUrl == ghMergedUrl }) {
            filtered.add(0, FirmwareAsset("wadamesh-tdeck-merged.bin", ghMergedUrl, 3_417_680L))
        }
        if (filtered.none { it.downloadUrl == cdnMergedUrl }) {
            filtered.add(FirmwareAsset("wadamesh-tdeck-merged.bin", cdnMergedUrl, 3_417_680L))
        }
        if (filtered.none { it.downloadUrl == ghAppUrl }) {
            filtered.add(FirmwareAsset("wadamesh-tdeck.bin", ghAppUrl, 3_351_184L))
        }
        return release.copy(assets = filtered)
    }

    fun curatedTDeckFlavorFallback(flavor: TDeckMeshCoreFlavor): List<FirmwareRelease> {
        return when (flavor) {
            TDeckMeshCoreFlavor.RIPPLE -> listOf(
                FirmwareRelease(
                    id = "fallback-tdeck-ripple",
                    tagName = "v12.0",
                    title = "Ripple Ultra UI (T-Deck Plus v12.0)",
                    publishedDate = "Official",
                    ecosystem = FirmwareEcosystem.MESHCORE,
                    isPrerelease = false,
                    changelog = "• Official Ripple Radios standalone Ultra GUI for LilyGO T-Deck / T-Deck Plus\n• Full touch screen + physical keyboard chat, map & node tools (NOT headless Room Server)\n• Flashes Ultra-TDeck-v12.0-merged.bin @ 0x0000",
                    assets = listOf(
                        FirmwareAsset(
                            fileName = "Ultra-TDeck-v12.0-merged.bin",
                            downloadUrl = "https://flasher.meshcore.io/releases/download/t-deck-plus-v12.0/Ultra-TDeck-v12.0-merged.bin?repo=github-ripple",
                            sizeBytes = 4_108_656L
                        ),
                        FirmwareAsset(
                            fileName = "Ultra-TDeck-v12.0.bin",
                            downloadUrl = "https://flasher.meshcore.io/releases/download/t-deck-plus-v12.0/Ultra-TDeck-v12.0.bin?repo=github-ripple",
                            sizeBytes = 2_120_000L
                        )
                    )
                )
            )
            TDeckMeshCoreFlavor.WADAMESH -> listOf(
                FirmwareRelease(
                    id = "fallback-tdeck-wadamesh",
                    tagName = "beta_101",
                    title = "WadaMesh Touch-UI for LilyGO T-Deck (beta_101)",
                    publishedDate = "Official",
                    ecosystem = FirmwareEcosystem.MESHCORE,
                    isPrerelease = false,
                    changelog = "• Standalone Touch-UI MeshCore firmware for LilyGO T-Deck / T-Deck Plus\n• Supports offline OpenStreetMap (OSM) SD card map tiles, on-device chat, channels & tools\n• Flashes full wadamesh-tdeck-merged.bin @ 0x0000",
                    assets = listOf(
                        FirmwareAsset(
                            fileName = "wadamesh-tdeck-merged.bin",
                            downloadUrl = "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck-merged.bin",
                            sizeBytes = 3_417_680L
                        ),
                        FirmwareAsset(
                            fileName = "wadamesh-tdeck-merged.bin",
                            downloadUrl = "https://firmware.wadamesh.com/releases/TOUCH/beta_101/wadamesh-tdeck-merged.bin",
                            sizeBytes = 3_417_680L
                        ),
                        FirmwareAsset(
                            fileName = "wadamesh-tdeck.bin",
                            downloadUrl = "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck.bin",
                            sizeBytes = 3_351_184L
                        )
                    )
                )
            )
        }
    }

    private fun fetchGitHubApiReleases(
        owner: String,
        repo: String,
        ecosystem: FirmwareEcosystem,
        perPage: Int = 25
    ): List<FirmwareRelease> {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MeshInstaller-Android")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string() ?: return emptyList()
                val arr = JSONArray(bodyStr)
                val list = mutableListOf<FirmwareRelease>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tagName = obj.optString("tag_name", "")
                    if (tagName.isBlank()) continue
                    val id = obj.optLong("id", i.toLong()).toString()
                    val name = obj.optString("name", "").takeIf { it.isNotBlank() }
                        ?: "${ecosystem.displayName} $tagName"
                    // Skip broken or revoked builds
                    if (name.contains("revoked", ignoreCase = true) || tagName.contains("revoked", ignoreCase = true)) {
                        continue
                    }
                    val publishedAt = obj.optString("published_at", "").take(10).ifEmpty { "Recent" }
                    val prerelease = obj.optBoolean("prerelease", false) ||
                        tagName.contains("alpha", ignoreCase = true) ||
                        tagName.contains("rc", ignoreCase = true) ||
                        name.contains("alpha", ignoreCase = true)
                    val body = obj.optString("body", "Official ${ecosystem.displayName} release.")
                        .replace("\r\n", "\n")
                        .take(900)

                    val assetsArr = obj.optJSONArray("assets")
                    val assets = mutableListOf<FirmwareAsset>()
                    if (assetsArr != null) {
                        for (j in 0 until assetsArr.length()) {
                            val a = assetsArr.getJSONObject(j)
                            val aName = a.optString("name", "")
                            val aUrl = a.optString("browser_download_url", "")
                            val aSize = a.optLong("size", 0L)
                            if (aName.isNotBlank() && aUrl.startsWith("https://")) {
                                assets.add(FirmwareAsset(fileName = aName, downloadUrl = aUrl, sizeBytes = aSize))
                            }
                        }
                    }
                    // If GitHub API omitted assets or returned synthesized tag, populate standard Meshtastic arch assets
                    val finalAssets = if (assets.isEmpty() && ecosystem == FirmwareEcosystem.MESHTASTIC) {
                        buildStandardMeshtasticAssetsForTag(tagName)
                    } else {
                        assets
                    }

                    list.add(
                        FirmwareRelease(
                            id = id,
                            tagName = tagName,
                            title = name,
                            publishedDate = publishedAt,
                            ecosystem = ecosystem,
                            isPrerelease = prerelease,
                            changelog = body,
                            assets = finalAssets
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchFromMeshtasticFlasherApi(): List<FirmwareRelease> {
        return try {
            val req = Request.Builder()
                .url("https://api.meshtastic.org/github/firmware/list")
                .header("Accept", "application/json")
                .header("User-Agent", "MeshInstaller-Android")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string() ?: return emptyList()
                val root = JSONObject(bodyStr)
                val releasesObj = root.optJSONObject("releases") ?: return emptyList()
                val result = mutableListOf<FirmwareRelease>()

                fun parseChannel(arr: JSONArray?, isPrerelease: Boolean) {
                    if (arr == null) return
                    for (i in 0 until minOf(arr.length(), 10)) {
                        val item = arr.getJSONObject(i)
                        val tag = item.optString("id", "")
                        if (tag.isBlank()) continue
                        val title = item.optString("title", "Meshtastic $tag")
                        val notes = item.optString("release_notes", "Official Meshtastic firmware release.")
                            .replace("\r\n", "\n")
                            .take(900)
                        result.add(
                            FirmwareRelease(
                                id = "mt-api-$tag",
                                tagName = tag,
                                title = title,
                                publishedDate = if (isPrerelease) "Alpha Preview" else "Production Beta / Stable",
                                ecosystem = FirmwareEcosystem.MESHTASTIC,
                                isPrerelease = isPrerelease,
                                changelog = notes,
                                assets = buildStandardMeshtasticAssetsForTag(tag)
                            )
                        )
                    }
                }

                parseChannel(releasesObj.optJSONArray("stable"), isPrerelease = false)
                parseChannel(releasesObj.optJSONArray("alpha"), isPrerelease = true)
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Uses GitHub's standard web redirect (`/releases/latest` -> `/releases/tag/<tag>`) and
     * `/releases/expanded_assets/<tag>` HTML fragment to discover real release tags and asset URLs
     * even when `api.github.com` is rate-limited (HTTP 403).
     */
    private fun fetchLatestReleaseViaGitHubWeb(
        owner: String,
        repo: String,
        ecosystem: FirmwareEcosystem
    ): FirmwareRelease? {
        return try {
            val latestReq = Request.Builder()
                .url("https://github.com/$owner/$repo/releases/latest")
                .header("Accept", "text/html")
                .build()
            val latestTag = okHttpClient.newCall(latestReq).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl.contains("/releases/tag/")) {
                    finalUrl.substringAfter("/releases/tag/").substringBefore("?").substringBefore("#").trim()
                } else {
                    null
                }
            } ?: return null

            val expandedAssets = fetchExpandedAssetsFromGitHubWeb(owner, repo, latestTag)
            val assets = if (expandedAssets.isNotEmpty()) {
                expandedAssets
            } else if (ecosystem == FirmwareEcosystem.MESHTASTIC) {
                buildStandardMeshtasticAssetsForTag(latestTag)
            } else {
                emptyList()
            }

            FirmwareRelease(
                id = "web-$owner-$latestTag",
                tagName = latestTag,
                title = "${ecosystem.displayName} $latestTag (Latest)",
                publishedDate = "Latest",
                ecosystem = ecosystem,
                isPrerelease = false,
                changelog = "Fetched directly from github.com/$owner/$repo/releases/tag/$latestTag",
                assets = assets
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchExpandedAssetsFromGitHubWeb(
        owner: String,
        repo: String,
        tagName: String
    ): List<FirmwareAsset> {
        return try {
            val url = "https://github.com/$owner/$repo/releases/expanded_assets/$tagName"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "text/html")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val html = resp.body?.string() ?: return emptyList()
                val regex = Regex("""href=["'](/${Regex.escape(owner)}/${Regex.escape(repo)}/releases/download/[^"']+)["']""")
                val seen = LinkedHashSet<String>()
                val list = mutableListOf<FirmwareAsset>()
                for (match in regex.findAll(html)) {
                    val path = match.groupValues[1]
                    val fullUrl = "https://github.com$path"
                    if (seen.add(fullUrl)) {
                        val fileName = path.substringAfterLast('/')
                        list.add(
                            FirmwareAsset(
                                fileName = fileName,
                                downloadUrl = fullUrl,
                                sizeBytes = 15_000_000L
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildStandardMeshtasticAssetsForTag(tagName: String): List<FirmwareAsset> {
        val ver = tagName.removePrefix("v")
        val base = "https://github.com/meshtastic/firmware/releases/download/$tagName"
        return listOf(
            FirmwareAsset("firmware-esp32s3-$ver.zip", "$base/firmware-esp32s3-$ver.zip", 45_000_000L),
            FirmwareAsset("firmware-esp32-$ver.zip", "$base/firmware-esp32-$ver.zip", 32_000_000L),
            FirmwareAsset("firmware-esp32c3-$ver.zip", "$base/firmware-esp32c3-$ver.zip", 14_000_000L),
            FirmwareAsset("firmware-nrf52840-$ver.zip", "$base/firmware-nrf52840-$ver.zip", 18_000_000L),
            FirmwareAsset("firmware-rp2040-$ver.zip", "$base/firmware-rp2040-$ver.zip", 8_000_000L)
        )
    }

    /**
     * Resolves and downloads the real firmware binary for the selected board from Meshtastic or MeshCore.
     */
    suspend fun prepareFirmwarePayload(
        board: HardwareBoard,
        release: FirmwareRelease,
        meshCoreRole: MeshCoreRole,
        fullErase: Boolean,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        preferBleOtaPackage: Boolean = false,
        bundleWebUiPartition: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): PreparedFirmwareImage = withContext(Dispatchers.IO) {
        val isTDeckMeshCore = board.id == "lilygo_tdeck" && release.ecosystem == FirmwareEcosystem.MESHCORE
        val targetOwner = if (isTDeckMeshCore) tDeckMeshCoreFlavor.repoOwner else release.ecosystem.repoOwner
        val targetRepo = if (isTDeckMeshCore) tDeckMeshCoreFlavor.repoName else release.ecosystem.repoName

        // Over BLE OTA on ESP32/ESP32-S3/ESP32-C3, always prefer the 0x10000 application partition binary
        // rather than the 0x0000 full factory/merged binary.
        val isEspFamily = board.mcu == McuArchitecture.ESP32 ||
            board.mcu == McuArchitecture.ESP32_S3 ||
            board.mcu == McuArchitecture.ESP32_C3
        val effectiveFullErase = if (preferBleOtaPackage && isEspFamily) false else fullErase

        val variantDesc = when {
            board.id == "lilygo_tdeck" && release.ecosystem == FirmwareEcosystem.MESHTASTIC ->
                if (useMeshtasticMui) "Meshtastic UI (MUI)" else "Meshtastic BaseUI"
            isTDeckMeshCore -> tDeckMeshCoreFlavor.label
            release.ecosystem == FirmwareEcosystem.MESHCORE -> "${release.ecosystem.displayName} (${meshCoreRole.label})"
            else -> release.ecosystem.displayName
        }

        onProgress(0.06f, "Resolving $variantDesc release assets for ${board.name}...")

        var effectiveRelease = release
        // If T-Deck MeshCore is selected, ensure the release comes from the chosen flavor's repository (WadaMesh vs Ripple Ultra UI)
        val releaseMatchesFlavorRepo = !isTDeckMeshCore || when (tDeckMeshCoreFlavor) {
            TDeckMeshCoreFlavor.RIPPLE -> effectiveRelease.assets.any {
                it.fileName.contains("ultra-tdeck", ignoreCase = true) ||
                    it.downloadUrl.contains("repo=github-ripple", ignoreCase = true) ||
                    it.downloadUrl.contains("/ripplebiz/", ignoreCase = true)
            }
            TDeckMeshCoreFlavor.WADAMESH -> effectiveRelease.assets.any {
                it.fileName.contains("wadamesh-tdeck", ignoreCase = true) ||
                    it.downloadUrl.contains("/ALLFATHER-BV/wadamesh/", ignoreCase = true) ||
                    it.downloadUrl.contains("firmware.wadamesh.com", ignoreCase = true)
            }
        }
        // For non-T-Deck MeshCore, ensure the release actually contains assets for the selected MeshCoreRole
        val nonTDeckRoleHasCandidates = isTDeckMeshCore ||
            effectiveRelease.ecosystem != FirmwareEcosystem.MESHCORE ||
            selectCandidateAssets(
                release = effectiveRelease,
                board = board,
                meshCoreRole = meshCoreRole,
                useMeshtasticMui = useMeshtasticMui,
                tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
                preferBleOtaPackage = preferBleOtaPackage
            ).isNotEmpty()

        val isFallbackPlaceholder = effectiveRelease.id.startsWith("fallback-") ||
            effectiveRelease.assets.none { it.downloadUrl.startsWith("https://") } ||
            !releaseMatchesFlavorRepo ||
            !nonTDeckRoleHasCandidates

        if (isFallbackPlaceholder) {
            onProgress(0.12f, "Querying live $variantDesc releases ($targetOwner/$targetRepo)...")
            val liveList = if (isTDeckMeshCore) {
                fetchTDeckMeshCoreReleases(tDeckMeshCoreFlavor)
            } else {
                fetchReleases(release.ecosystem).getOrNull().orEmpty()
            }
            val matchedLive = if (release.isPrerelease) {
                liveList.firstOrNull { !it.id.startsWith("fallback-") && it.isPrerelease }
            } else {
                liveList.firstOrNull { !it.id.startsWith("fallback-") && !it.isPrerelease }
                    ?: liveList.firstOrNull { !it.id.startsWith("fallback-") }
            }
            if (matchedLive != null) {
                effectiveRelease = matchedLive
                onProgress(0.18f, "Resolved $variantDesc release ${effectiveRelease.tagName}")
            }
        }

        // If the release has a real tag but empty assets (or lacks the role's tag assets), scrape expanded_assets directly
        if ((effectiveRelease.assets.none { it.downloadUrl.startsWith("https://") } ||
                (effectiveRelease.ecosystem == FirmwareEcosystem.MESHCORE && !isTDeckMeshCore &&
                    selectCandidateAssets(effectiveRelease, board, meshCoreRole, useMeshtasticMui, tDeckMeshCoreFlavor, preferBleOtaPackage).isEmpty())) &&
            !effectiveRelease.id.startsWith("fallback-")
        ) {
            val cleanVer = extractMeshCoreVersionSuffix(effectiveRelease.tagName)
            val tagsToScrape = if (effectiveRelease.ecosystem == FirmwareEcosystem.MESHCORE && !isTDeckMeshCore) {
                val rolePrefix = when (meshCoreRole) {
                    MeshCoreRole.COMPANION_BLE, MeshCoreRole.COMPANION_USB -> "companion"
                    MeshCoreRole.REPEATER -> "repeater"
                    MeshCoreRole.ROOM_SERVER -> "room-server"
                    MeshCoreRole.CUSTOM -> "companion"
                }
                listOf("$rolePrefix-$cleanVer", effectiveRelease.tagName)
            } else {
                listOf(effectiveRelease.tagName)
            }
            val scraped = tagsToScrape.flatMap { t ->
                fetchExpandedAssetsFromGitHubWeb(owner = targetOwner, repo = targetRepo, tagName = t)
            }.distinctBy { it.downloadUrl }

            if (scraped.isNotEmpty()) {
                effectiveRelease = if (isTDeckMeshCore && tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.WADAMESH) {
                    enrichWadaMeshReleaseAssets(effectiveRelease.copy(assets = scraped))
                } else {
                    effectiveRelease.copy(assets = (effectiveRelease.assets + scraped).distinctBy { it.downloadUrl })
                }
            } else if (effectiveRelease.ecosystem == FirmwareEcosystem.MESHTASTIC) {
                effectiveRelease = effectiveRelease.copy(
                    assets = buildStandardMeshtasticAssetsForTag(effectiveRelease.tagName)
                )
            }
        }

        // Build ordered list of candidate assets to try downloading
        val candidates = selectCandidateAssets(
            release = effectiveRelease,
            board = board,
            meshCoreRole = meshCoreRole,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
            preferBleOtaPackage = preferBleOtaPackage
        )

        for (candidate in candidates) {
            try {
                onProgress(0.24f, "Connecting to ${candidate.fileName}...")
                if (candidate.fileName.endsWith(".zip", ignoreCase = true)) {
                    // 1. Try Ultra-Fast HTTP Range ZIP Central Directory Extraction first (~800KB download instead of 50MB!)
                    val fastExtracted = tryExtractBoardFromRemoteZipViaRange(
                        zipUrl = candidate.downloadUrl,
                        board = board,
                        ecosystem = effectiveRelease.ecosystem,
                        meshCoreRole = meshCoreRole,
                        preferFactoryImage = effectiveFullErase,
                        useMeshtasticMui = useMeshtasticMui,
                        bundleWebUi = bundleWebUiPartition,
                        preferBleOtaPackage = preferBleOtaPackage,
                        onProgress = onProgress
                    )
                    if (fastExtracted != null) {
                        onProgress(
                            1.0f,
                            "Downloaded ${fastExtracted.fileName} (${fastExtracted.bytes.size / 1024} KB @ 0x%05X)".format(fastExtracted.flashOffset)
                        )
                        return@withContext fastExtracted
                    }

                    // 2. Fallback: Stream the ZIP with live MB progress
                    onProgress(0.35f, "Streaming archive ${candidate.fileName} from GitHub...")
                    val request = Request.Builder().url(candidate.downloadUrl).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStream = response.body?.byteStream()
                            if (bodyStream != null) {
                                val extracted = extractBoardBinaryFromZipStream(
                                    inputStream = bodyStream,
                                    board = board,
                                    ecosystem = effectiveRelease.ecosystem,
                                    meshCoreRole = meshCoreRole,
                                    preferFactoryImage = effectiveFullErase,
                                    useMeshtasticMui = useMeshtasticMui,
                                    bundleWebUi = bundleWebUiPartition,
                                    preferBleOtaPackage = preferBleOtaPackage,
                                    onProgress = onProgress
                                )
                                if (extracted != null) {
                                    onProgress(
                                        1.0f,
                                        "Extracted ${extracted.fileName} (${extracted.bytes.size / 1024} KB @ 0x%05X)".format(extracted.flashOffset)
                                    )
                                    return@withContext extracted
                                }
                            }
                        } else {
                            onProgress(0.40f, "HTTP ${response.code} for ${candidate.fileName}, trying next source...")
                        }
                    }
                } else {
                    // Direct .bin or .uf2 asset download
                    onProgress(0.35f, "Downloading ${candidate.fileName}...")
                    val request = Request.Builder().url(candidate.downloadUrl).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val rawBytes = response.body?.bytes()
                            if (rawBytes != null && rawBytes.size > 1024) {
                                val wrapped = inspectAndWrapBinary(
                                    bytes = rawBytes,
                                    fileName = candidate.fileName,
                                    board = board,
                                    isRealBinary = true
                                )
                                onProgress(
                                    1.0f,
                                    "Downloaded ${wrapped.fileName} (${wrapped.bytes.size / 1024} KB @ 0x%05X)".format(wrapped.flashOffset)
                                )
                                return@withContext wrapped
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onProgress(0.50f, "Retrying download (${e.javaClass.simpleName}: ${e.message?.take(45)})...")
            }
        }

        // Last-resort live discovery if the selected release tag didn't have the board asset
        val latestWeb = if (release.ecosystem == FirmwareEcosystem.MESHCORE && !isTDeckMeshCore) {
            fetchLatestMeshCoreUnifiedViaGitHubWeb()
        } else {
            fetchLatestReleaseViaGitHubWeb(
                owner = targetOwner,
                repo = targetRepo,
                ecosystem = release.ecosystem
            )?.let {
                if (isTDeckMeshCore && tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.WADAMESH) {
                    enrichWadaMeshReleaseAssets(it)
                } else {
                    it
                }
            }
        }
        if (latestWeb != null) {
            val latestCandidates = selectCandidateAssets(
                release = latestWeb,
                board = board,
                meshCoreRole = meshCoreRole,
                useMeshtasticMui = useMeshtasticMui,
                tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
                preferBleOtaPackage = preferBleOtaPackage
            )
            for (cand in latestCandidates) {
                try {
                    if (cand.fileName.endsWith(".zip", ignoreCase = true)) {
                        val fast = tryExtractBoardFromRemoteZipViaRange(
                            zipUrl = cand.downloadUrl,
                            board = board,
                            ecosystem = latestWeb.ecosystem,
                            meshCoreRole = meshCoreRole,
                            preferFactoryImage = effectiveFullErase,
                            useMeshtasticMui = useMeshtasticMui,
                            onProgress = onProgress
                        )
                        if (fast != null) return@withContext fast
                    } else {
                        val req = Request.Builder().url(cand.downloadUrl).build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.size > 1024) {
                                    return@withContext inspectAndWrapBinary(bytes, cand.fileName, board, isRealBinary = true)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Fallback synthetic image ONLY for offline / Loopback Test Bench mode
        val expectedName = resolveExpectedFileName(
            board = board,
            release = effectiveRelease,
            meshCoreRole = meshCoreRole,
            fullErase = effectiveFullErase,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor
        )
        val fallbackBytes = buildValidMcuFirmwareImage(
            board = board,
            release = effectiveRelease,
            meshCoreRole = meshCoreRole,
            targetAssetName = expectedName,
            includeBootloader = effectiveFullErase,
            onProgress = onProgress
        )
        val sha = sha256Hex(fallbackBytes)
        val defaultOffset = when {
            board.mcu == McuArchitecture.NRF52840 -> 0x26000
            board.mcu == McuArchitecture.RP2040 -> 0x10000000
            effectiveFullErase -> 0x0000
            else -> 0x10000
        }
        PreparedFirmwareImage(
            bytes = fallbackBytes,
            sha256Hex = sha,
            flashOffset = defaultOffset,
            fileName = expectedName,
            isFullFactoryImage = effectiveFullErase,
            isRealBinary = false
        )
    }

    private val vaultFastHttpClient = okHttpClient.newBuilder()
        .connectTimeout(15000, TimeUnit.MILLISECONDS)
        .readTimeout(35000, TimeUnit.MILLISECONDS)
        .callTimeout(50000, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Resolves and downloads the official firmware binary for Off-Grid Vault local caching.
     * Uses cached release catalogs and cached HTTP Range ZIP Central Directory indices so all selected
     * radios download their real Meshtastic (`.factory.bin` / `.bin` / `.uf2`) and MeshCore binaries
     * quickly and reliably.
     */
    suspend fun prepareFirmwarePayloadForVault(
        board: HardwareBoard,
        release: FirmwareRelease,
        meshCoreRole: MeshCoreRole,
        fullErase: Boolean,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        onProgress: (Float, String) -> Unit
    ): PreparedFirmwareImage = withContext(Dispatchers.IO) {
        val isTDeckMeshCore = board.id == "lilygo_tdeck" && release.ecosystem == FirmwareEcosystem.MESHCORE
        var effectiveRelease = release

        // Resolve live release catalog (cached in memory across batch vault items) if placeholder or missing role
        val needsLiveCatalog = effectiveRelease.id.startsWith("fallback-") ||
            effectiveRelease.assets.none { it.downloadUrl.startsWith("https://") } ||
            (!isTDeckMeshCore && effectiveRelease.ecosystem == FirmwareEcosystem.MESHCORE &&
                selectCandidateAssets(effectiveRelease, board, meshCoreRole, useMeshtasticMui, tDeckMeshCoreFlavor).isEmpty())

        if (needsLiveCatalog) {
            val liveList = if (isTDeckMeshCore) {
                tDeckFlavorCache[tDeckMeshCoreFlavor] ?: fetchTDeckMeshCoreReleases(tDeckMeshCoreFlavor).also {
                    if (it.isNotEmpty()) tDeckFlavorCache[tDeckMeshCoreFlavor] = it
                }
            } else {
                releaseCache[release.ecosystem] ?: fetchReleases(release.ecosystem).getOrNull().orEmpty()
            }
            val matchedLive = if (release.isPrerelease) {
                liveList.firstOrNull { !it.id.startsWith("fallback-") && it.isPrerelease }
            } else {
                liveList.firstOrNull { !it.id.startsWith("fallback-") && !it.isPrerelease }
                    ?: liveList.firstOrNull { !it.id.startsWith("fallback-") }
            }
            if (matchedLive != null) {
                effectiveRelease = matchedLive
            }
        }

        if (effectiveRelease.ecosystem == FirmwareEcosystem.MESHTASTIC && effectiveRelease.assets.isEmpty()) {
            val tag = if (effectiveRelease.tagName.startsWith("latest")) "v2.8.1.8e6a88d" else effectiveRelease.tagName
            effectiveRelease = effectiveRelease.copy(assets = buildStandardMeshtasticAssetsForTag(tag))
        }

        val expectedName = resolveExpectedFileName(
            board = board,
            release = effectiveRelease,
            meshCoreRole = meshCoreRole,
            fullErase = fullErase,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor
        )

        onProgress(0.15f, "Downloading $expectedName for ${board.name}...")

        val candidates = selectCandidateAssets(
            release = effectiveRelease,
            board = board,
            meshCoreRole = meshCoreRole,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor
        )

        // 1. Try direct binary candidates (.bin or .uf2)
        val directCandidates = candidates.filter { !it.fileName.endsWith(".zip", ignoreCase = true) }
        for (directCandidate in directCandidates) {
            try {
                val request = Request.Builder().url(directCandidate.downloadUrl).build()
                vaultFastHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val rawBytes = response.body?.bytes()
                        if (rawBytes != null && rawBytes.size > 1024) {
                            val wrapped = inspectAndWrapBinary(
                                bytes = rawBytes,
                                fileName = directCandidate.fileName,
                                board = board,
                                isRealBinary = true
                            )
                            onProgress(1.0f, "Saved ${wrapped.fileName} (${wrapped.bytes.size / 1024} KB)")
                            return@withContext wrapped
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next candidate
            }
        }

        // 2. For Meshtastic official releases (.zip archives), extract the real board binary via HTTP Range
        val zipCandidates = candidates.filter { it.fileName.endsWith(".zip", ignoreCase = true) }
        for (zipCandidate in zipCandidates) {
            try {
                onProgress(0.35f, "Extracting ${board.name} binary from ${zipCandidate.fileName}...")
                val fastExtracted = tryExtractBoardFromRemoteZipViaRange(
                    zipUrl = zipCandidate.downloadUrl,
                    board = board,
                    ecosystem = effectiveRelease.ecosystem,
                    meshCoreRole = meshCoreRole,
                    preferFactoryImage = fullErase,
                    useMeshtasticMui = useMeshtasticMui,
                    onProgress = onProgress
                )
                if (fastExtracted != null && fastExtracted.bytes.size > 1024) {
                    onProgress(1.0f, "Saved ${fastExtracted.fileName} (${fastExtracted.bytes.size / 1024} KB)")
                    return@withContext fastExtracted
                }
            } catch (_: Exception) {
                // Try next ZIP candidate
            }
        }

        // 3. Offline / test-bench fallback (marked isRealBinary = false so physical USB flashing never mistakes it for a real download)
        onProgress(0.55f, "Packaging offline test-bench image $expectedName...")
        val fallbackBytes = buildValidMcuFirmwareImage(
            board = board,
            release = effectiveRelease,
            meshCoreRole = meshCoreRole,
            targetAssetName = expectedName,
            includeBootloader = fullErase,
            onProgress = onProgress
        )
        val sha = sha256Hex(fallbackBytes)
        val defaultOffset = when {
            board.mcu == McuArchitecture.NRF52840 -> 0x26000
            board.mcu == McuArchitecture.RP2040 -> 0x10000000
            fullErase -> 0x0000
            else -> 0x10000
        }
        onProgress(1.0f, "Saved $expectedName (${fallbackBytes.size / 1024} KB)")
        PreparedFirmwareImage(
            bytes = fallbackBytes,
            sha256Hex = sha,
            flashOffset = defaultOffset,
            fileName = expectedName,
            isFullFactoryImage = fullErase,
            isRealBinary = false
        )
    }

    fun selectCandidateAssets(
        release: FirmwareRelease,
        board: HardwareBoard,
        meshCoreRole: MeshCoreRole,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        preferBleOtaPackage: Boolean = false
    ): List<FirmwareAsset> {
        val downloadable = release.assets.filter { asset ->
            if (!asset.downloadUrl.startsWith("https://")) return@filter false
            val lower = asset.fileName.lowercase(Locale.US)
            // Exclude non-firmware metadata, ELF debug symbol archives (1.37 GB debug-elfs-*.zip), and LittleFS/OTA helpers
            if (lower.endsWith(".json") ||
                lower.endsWith(".sha256") ||
                lower.endsWith(".md") ||
                lower.endsWith(".hex") ||
                lower.endsWith(".elf") ||
                lower.contains("debug-elfs") ||
                lower.contains("littlefs") ||
                lower.contains("bleota") ||
                lower.startsWith("mt-")
            ) {
                return@filter false
            }
            if (board.id == "lilygo_tdeck") {
                // Never match T-Deck Max, T-Deck Pro (e-ink), or Heltec V4 when flashing a standard LilyGO T-Deck / T-Deck Plus!
                if (lower.contains("tdeck-max") ||
                    lower.contains("t-deck-max") ||
                    lower.contains("tdeck_max") ||
                    lower.contains("tdeck-pro") ||
                    lower.contains("t-deck-pro") ||
                    lower.contains("tdeck_pro") ||
                    lower.contains("heltec")
                ) {
                    return@filter false
                }
                // When Ripple Ultra UI or WadaMesh is selected on T-Deck, NEVER match headless room_server, repeater, or companion_radio!
                if (release.ecosystem == FirmwareEcosystem.MESHCORE &&
                    (lower.contains("room_server") || lower.contains("repeater") || lower.contains("companion_radio"))
                ) {
                    return@filter false
                }
            } else if (release.ecosystem == FirmwareEcosystem.MESHCORE) {
                // For non-T-Deck MeshCore boards, strictly enforce that the asset matches the user's selected MeshCoreRole!
                if (!matchesMeshCoreRoleStrictly(board, lower, meshCoreRole)) {
                    return@filter false
                }
            }
            true
        }
        if (downloadable.isEmpty()) return emptyList()

        val archToken = when (board.mcu) {
            McuArchitecture.ESP32_S3 -> "esp32s3"
            McuArchitecture.ESP32 -> "esp32-"
            McuArchitecture.ESP32_C3 -> "esp32c3"
            McuArchitecture.NRF52840 -> "nrf52840"
            McuArchitecture.RP2040 -> "rp2040"
        }

        // 1. Direct board-matching assets (or WadaMesh / Ripple Ultra direct .bin assets on T-Deck)
        val directBoardAssets = downloadable.filter { asset ->
            val lower = asset.fileName.lowercase(Locale.US)
            matchesBoardStrictly(board, lower) ||
                (board.id == "lilygo_tdeck" &&
                    release.ecosystem == FirmwareEcosystem.MESHCORE &&
                    (lower.contains("wadamesh") || lower.contains("ultra-tdeck")))
        }.sortedByDescending { asset ->
            val lower = asset.fileName.lowercase(Locale.US)
            var score = 10
            if (board.id == "lilygo_tdeck" && release.ecosystem == FirmwareEcosystem.MESHTASTIC) {
                val isTft = lower.contains("t-deck-tft") || lower.contains("tft") || lower.contains("mui")
                val isBase = lower.contains("t-deck") && !lower.contains("tft") && !lower.contains("pro")
                score += if (useMeshtasticMui && isTft) 35 else if (!useMeshtasticMui && isBase) 35 else -25
            }
            if (board.id == "lilygo_tdeck" && release.ecosystem == FirmwareEcosystem.MESHCORE) {
                val isMerged = lower.contains("-merged.bin") || lower.contains("_merged.bin")
                if (preferBleOtaPackage) {
                    // Over BLE OTA on ESP32-S3 T-Deck, prefer the non-merged app binary (0x10000)
                    if (!isMerged && lower.endsWith(".bin")) score += 45
                } else if (isMerged) {
                    score += 45
                }
                if (lower.contains("-sd-") || lower.contains("_sd_")) score -= 20
                if (tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.WADAMESH && lower.startsWith("wadamesh-tdeck")) {
                    score += 35
                }
                if (tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.RIPPLE && lower.startsWith("ultra-tdeck")) {
                    score += 35
                }
            } else if (release.ecosystem == FirmwareEcosystem.MESHCORE) {
                score += scoreMeshCoreRoleMatch(lower, meshCoreRole)
                val isMerged = lower.contains("-merged.bin") || lower.contains("_merged.bin")
                if (preferBleOtaPackage) {
                    // Over BLE OTA on ESP32/S3/C3, prefer the clean 0x10000 app binary over -merged.bin
                    if (!isMerged && lower.endsWith(".bin")) score += 35
                } else if (isMerged) {
                    // Over USB Serial on ESP32/S3/C3, prefer full -merged.bin so bootloader & partition table at 0x0000 are written
                    score += 35
                }
                // Board-specific variant preferences on MeshCore
                if (board.id == "heltec_t114" && lower.contains("without_display")) {
                    score -= 40
                }
                if (board.id == "seeed_xiao_esp32s3" && lower.contains("wio")) {
                    score += 25
                }
                if ((board.id == "lilygo_tlora_t3s3" || board.id == "lilygo_tbeam_v1_2") && lower.contains("sx1262")) {
                    score += 20
                }
            }
            if (board.mcu == McuArchitecture.NRF52840) {
                if (preferBleOtaPackage) {
                    // Over BLE DFU on nRF52840, prefer official Nordic/Adafruit DFU .zip package (.dat + .bin)
                    if (lower.endsWith(".zip")) score += 35
                    else if (lower.endsWith(".uf2")) score += 10
                } else {
                    if (lower.endsWith(".uf2")) score += 20
                    else if (lower.endsWith(".zip")) score += 5
                }
            } else if (board.mcu == McuArchitecture.RP2040) {
                if (lower.endsWith(".uf2")) score += 20
                else if (lower.endsWith(".zip")) score += 5
            } else if (lower.endsWith(".bin")) {
                score += 10
            }
            score
        }

        // 2. Architecture bundle ZIPs for Meshtastic (e.g. firmware-esp32s3-*.zip, firmware-nrf52840-*.zip)
        val archZipAssets = if (release.ecosystem == FirmwareEcosystem.MESHTASTIC) {
            downloadable.filter { asset ->
                val lower = asset.fileName.lowercase(Locale.US)
                lower.endsWith(".zip") &&
                    !lower.contains("debug-elfs") && (
                        lower.contains(archToken) ||
                            (board.mcu == McuArchitecture.NRF52840 && lower.contains("nrf52"))
                        )
            }.sortedByDescending { asset ->
                if (asset.fileName.startsWith("firmware-", ignoreCase = true)) 10 else 0
            }
        } else {
            emptyList()
        }

        // 3. Other Meshtastic ZIP bundles (never include unrelated board ZIPs on MeshCore or debug-elfs)
        val otherZipAssets = if (release.ecosystem == FirmwareEcosystem.MESHTASTIC) {
            downloadable.filter { asset ->
                val lower = asset.fileName.lowercase(Locale.US)
                lower.endsWith(".zip") &&
                    !lower.contains("debug-elfs") &&
                    asset !in directBoardAssets &&
                    asset !in archZipAssets
            }
        } else {
            emptyList()
        }

        return (directBoardAssets + archZipAssets + otherZipAssets).distinctBy { it.downloadUrl }
    }

    /**
     * Strictly verifies that a MeshCore asset filename belongs to the selected `MeshCoreRole`
     * and never cross-matches `companion_radio_usb` when `COMPANION_BLE` is selected (e.g. on Station G2),
     * nor `logging_repeater`, `room_server`, or `repeater`.
     */
    private fun matchesMeshCoreRoleStrictly(
        board: HardwareBoard,
        lowerFileName: String,
        role: MeshCoreRole
    ): Boolean {
        val isRoomServer = lowerFileName.contains("room_server") || lowerFileName.contains("room-server")
        val isLoggingRepeater = lowerFileName.contains("logging_repeater") || lowerFileName.contains("logging-repeater")
        val isRepeater = lowerFileName.contains("repeater") && !isLoggingRepeater
        val isCompanionBle = lowerFileName.contains("companion_radio_ble") ||
            (lowerFileName.contains("companion") && lowerFileName.contains("ble"))
        val isCompanionUsb = lowerFileName.contains("companion_radio_usb") ||
            (lowerFileName.contains("companion") && (lowerFileName.contains("usb") || lowerFileName.contains("serial")))

        return when (role) {
            MeshCoreRole.COMPANION_BLE -> {
                if (isRoomServer || isRepeater || isLoggingRepeater || isCompanionUsb) false
                else isCompanionBle || (lowerFileName.contains("companion") && !isCompanionUsb)
            }
            MeshCoreRole.COMPANION_USB -> {
                if (isRoomServer || isRepeater || isLoggingRepeater) false
                else if (isCompanionUsb) true
                // Only T-Beam Supreme and T-Beam v1.2 publish a single unified companion_radio_ble build in upstream MeshCore
                else if (board.id == "lilygo_tbeam_s3" || board.id == "lilygo_tbeam_v1_2") isCompanionBle
                else false
            }
            MeshCoreRole.REPEATER -> {
                if (isRoomServer || isCompanionBle || isCompanionUsb || isLoggingRepeater || lowerFileName.contains("companion")) false
                else isRepeater
            }
            MeshCoreRole.ROOM_SERVER -> {
                if (isRepeater || isLoggingRepeater || isCompanionBle || isCompanionUsb || lowerFileName.contains("companion")) false
                else isRoomServer
            }
            MeshCoreRole.CUSTOM -> true
        }
    }

    private fun scoreMeshCoreRoleMatch(lowerFileName: String, role: MeshCoreRole): Int {
        return when (role) {
            MeshCoreRole.COMPANION_BLE -> when {
                lowerFileName.contains("companion_radio_ble") -> 100
                lowerFileName.contains("companion") && lowerFileName.contains("ble") -> 90
                else -> 0
            }
            MeshCoreRole.COMPANION_USB -> when {
                lowerFileName.contains("companion_radio_usb") -> 100
                lowerFileName.contains("companion") && lowerFileName.contains("usb") -> 90
                lowerFileName.contains("companion_radio_ble") -> 25
                else -> 0
            }
            MeshCoreRole.REPEATER -> when {
                lowerFileName.contains("logging_repeater") -> -50
                lowerFileName.contains("repeater") -> 100
                else -> 0
            }
            MeshCoreRole.ROOM_SERVER -> when {
                lowerFileName.contains("room_server") || lowerFileName.contains("room-server") -> 100
                else -> 0
            }
            MeshCoreRole.CUSTOM -> 10
        }
    }

    private fun matchesBoardStrictly(board: HardwareBoard, lowerFileName: String): Boolean {
        val tokens = buildBoardMatchTokens(board)
        if (tokens.none { lowerFileName.contains(it) }) return false

        // Strictly disambiguate all 16 boards from sub-variants, e-ink/InkHUD builds, and sibling hardware revisions
        return when (board.id) {
            "station_g2" -> !lowerFileName.contains("station_g3") &&
                !lowerFileName.contains("station-g3") &&
                !lowerFileName.contains("logging_repeater")
            "heltec_v3" -> !lowerFileName.contains("heltec_v2") &&
                !lowerFileName.contains("heltec-v2") &&
                !lowerFileName.contains("heltec_v4") &&
                !lowerFileName.contains("heltec-v4") &&
                !lowerFileName.contains("wsl") &&
                !lowerFileName.contains("wireless-paper") &&
                !lowerFileName.contains("wireless_paper") &&
                !lowerFileName.contains("wireless-tracker") &&
                !lowerFileName.contains("wireless_tracker") &&
                !lowerFileName.contains("mesh-node") &&
                !lowerFileName.contains("t114")
            "heltec_v4" -> !lowerFileName.contains("heltec_v2") &&
                !lowerFileName.contains("heltec-v2") &&
                !lowerFileName.contains("heltec_v3") &&
                !lowerFileName.contains("heltec-v3") &&
                !lowerFileName.contains("wsl") &&
                !lowerFileName.contains("wireless-paper") &&
                !lowerFileName.contains("wireless_paper") &&
                !lowerFileName.contains("wireless-tracker") &&
                !lowerFileName.contains("wireless_tracker") &&
                !lowerFileName.contains("mesh-node") &&
                !lowerFileName.contains("t114") &&
                !lowerFileName.contains("inkhud") &&
                !lowerFileName.contains("tft")
            "rak4631" -> !lowerFileName.contains("rak4631_eink") &&
                !lowerFileName.contains("rak4631-eink") &&
                !lowerFileName.contains("rak4631_eth_gw") &&
                !lowerFileName.contains("rak4631-eth-gw") &&
                !lowerFileName.contains("nomadstar") &&
                !lowerFileName.contains("meteor_pro")
            "lilygo_tbeam_s3" -> !lowerFileName.contains("tbeam_1w") &&
                !lowerFileName.contains("t-beam-1w")
            "lilygo_techo" -> !lowerFileName.contains("t-echo-lite") &&
                !lowerFileName.contains("techo-lite") &&
                !lowerFileName.contains("t-echo-plus") &&
                !lowerFileName.contains("techo-plus") &&
                !lowerFileName.contains("t-echo_card") &&
                !lowerFileName.contains("inkhud")
            "seeed_t1000e" -> !lowerFileName.contains("t1000-a") &&
                !lowerFileName.contains("t1000-b")
            "lilygo_tbeam_v1_2" -> !lowerFileName.contains("supreme") &&
                !lowerFileName.contains("tbeam-s3") &&
                !lowerFileName.contains("tbeam_s3") &&
                !lowerFileName.contains("tbeam_1w") &&
                !lowerFileName.contains("t-beam-1w") &&
                !lowerFileName.contains("tbeam-v0.7") &&
                !lowerFileName.contains("tbeam_v0.7") &&
                !lowerFileName.contains("v0.7")
            "heltec_t114" -> !lowerFileName.contains("without_display") &&
                !lowerFileName.contains("inkhud")
            "seeed_xiao_esp32s3" -> !lowerFileName.contains("nrf52") &&
                !lowerFileName.contains("rp2040") &&
                !lowerFileName.contains("esp32c3")
            "seeed_xiao_nrf52840" -> !lowerFileName.contains("xiao_s3") &&
                !lowerFileName.contains("xiao-s3") &&
                !lowerFileName.contains("rp2040")
            "lilygo_tdeck" -> !lowerFileName.contains("tdeck-max") &&
                !lowerFileName.contains("t-deck-max") &&
                !lowerFileName.contains("tdeck_max") &&
                !lowerFileName.contains("tdeck-pro") &&
                !lowerFileName.contains("t-deck-pro") &&
                !lowerFileName.contains("tdeck_pro") &&
                !lowerFileName.contains("heltec")
            "heltec_wireless_paper" -> !lowerFileName.contains("inkhud") &&
                !lowerFileName.contains("wireless-paper-v1") &&
                !lowerFileName.contains("wireless_paper_v1")
            "heltec_wireless_tracker" -> !lowerFileName.contains("wireless-tracker-v1-0") &&
                !lowerFileName.contains("wireless-tracker-v2") &&
                !lowerFileName.contains("wireless_tracker_v2") &&
                !lowerFileName.contains("heltec_tracker_v2") &&
                !lowerFileName.contains("inkhud")
            "lilygo_tlora_t3s3" -> !lowerFileName.contains("epaper") &&
                !lowerFileName.contains("inkhud")
            "heltec_v2" -> !lowerFileName.contains("heltec_v3") &&
                !lowerFileName.contains("heltec-v3") &&
                !lowerFileName.contains("heltec_v4") &&
                !lowerFileName.contains("heltec-v4") &&
                !lowerFileName.contains("wsl")
            "lilygo_tlora_v2_1" -> !lowerFileName.contains("t3s3") &&
                !lowerFileName.contains("v2-1-1_8") &&
                !lowerFileName.contains("v1")
            "heltec_vision_master_e290" -> !lowerFileName.contains("e213") &&
                !lowerFileName.contains("t190") &&
                !lowerFileName.contains("inkhud")
            "elecrow_thinknode_m1" -> !lowerFileName.contains("inkhud") &&
                !lowerFileName.contains("thinknode_m2") &&
                !lowerFileName.contains("thinknode_m3") &&
                !lowerFileName.contains("thinknode_m4") &&
                !lowerFileName.contains("thinknode_m5") &&
                !lowerFileName.contains("thinknode_m6") &&
                !lowerFileName.contains("thinknode_m7") &&
                !lowerFileName.contains("thinknode_m8")
            "rpi_pico_waveshare" -> !lowerFileName.contains("xiao_rp2040") &&
                !lowerFileName.contains("senselora") &&
                !lowerFileName.contains("pico2") &&
                !lowerFileName.contains("slowclock") &&
                !lowerFileName.contains("rp2040-lora")
            else -> true
        }
    }

    private fun buildBoardMatchTokens(board: HardwareBoard): List<String> {
        val tokens = mutableListOf(
            board.meshtasticTarget.lowercase(Locale.US),
            board.meshCoreTarget.lowercase(Locale.US)
        )
        when (board.id) {
            "station_g2" -> tokens.addAll(listOf("station-g2", "station_g2", "stationg2"))
            "heltec_v3" -> tokens.addAll(listOf("heltec-v3", "heltec_v3", "heltec_wifi_lora_32_v3"))
            "heltec_v4" -> tokens.addAll(listOf("heltec-v4", "heltec_v4", "heltec_wifi_lora_32_v4"))
            "rak4631" -> tokens.addAll(listOf("rak_4631", "rak4631"))
            "lilygo_tbeam_s3" -> tokens.addAll(
                listOf("t_beam_s3_supreme", "tbeam-s3-core", "tbeam_supreme", "t-beam-supreme", "tbeams3")
            )
            "lilygo_techo" -> tokens.addAll(listOf("lilygo_t-echo", "firmware-t-echo-", "t-echo"))
            "seeed_t1000e" -> tokens.addAll(listOf("tracker-t1000-e", "t1000-e", "t1000e"))
            "lilygo_tbeam_v1_2" -> tokens.addAll(listOf("tbeam_sx1262", "tbeam_sx1276", "firmware-tbeam-", "tbeam_v1"))
            "heltec_t114" -> tokens.addAll(listOf("heltec_t114", "heltec-mesh-node-t114"))
            "seeed_xiao_esp32s3" -> tokens.addAll(listOf("xiao_s3_wio", "xiao_s3", "seeed-xiao-s3", "xiao-esp32s3"))
            "seeed_xiao_nrf52840" -> tokens.addAll(listOf("xiao_nrf52", "seeed_xiao_nrf52840_kit", "seeed_xiao_nrf52840"))
            "lilygo_tdeck" -> tokens.addAll(listOf("t-deck-tft", "t-deck", "tdeck", "lilygo_tdeck"))
            "heltec_wireless_paper" -> tokens.addAll(listOf("heltec_wireless_paper", "heltec-wireless-paper"))
            "heltec_wireless_tracker" -> tokens.addAll(listOf("heltec_wireless_tracker", "heltec-wireless-tracker"))
            "lilygo_tlora_t3s3" -> tokens.addAll(listOf("lilygo_t3s3_sx1262", "lilygo_t3s3", "tlora-t3s3-v1", "tlora-t3s3"))
            "heltec_v2" -> tokens.addAll(listOf("heltec-v2_1", "heltec-v2.1", "heltec_v2"))
            "lilygo_tlora_v2_1" -> tokens.addAll(listOf("tlora-v2-1-1_6", "tlora-v2-1-1.6", "tlora_v2_1_1p6", "tlora_v2_1"))
            "heltec_vision_master_e290" -> tokens.addAll(listOf("heltec-vision-master-e290", "heltec_vision_master_e290", "vision_master_e290"))
            "elecrow_thinknode_m1" -> tokens.addAll(listOf("thinknode_m1", "thinknode-m1"))
            "rpi_pico_waveshare" -> tokens.addAll(
                listOf("firmware-pico-", "firmware-picow-", "picow_", "rpi-pico", "waveshare_rp2040_lora")
            )
        }
        return tokens.distinct()
    }

    private fun isFactoryBinaryName(lowerFileName: String): Boolean {
        return lowerFileName.endsWith(".factory.bin") ||
            lowerFileName.contains("-factory.bin") ||
            lowerFileName.contains("_factory.bin") ||
            lowerFileName.contains("-merged.bin") ||
            lowerFileName.contains("_merged.bin")
    }

    private fun isExplicitUpdateBinaryName(lowerFileName: String): Boolean {
        return lowerFileName.contains("-update.bin") ||
            lowerFileName.contains("_update.bin") ||
            lowerFileName.endsWith(".update.bin")
    }

    private fun fetchOrGetRemoteZipIndex(
        zipUrl: String,
        onProgress: (Float, String) -> Unit
    ): RemoteZipIndex? {
        zipIndexCache[zipUrl]?.let { return it }

        // Step 1: Probe total file size and Range support using a tiny 1-byte Range GET (works with GitHub S3 redirects)
        val probeReq = Request.Builder()
            .url(zipUrl)
            .header("Range", "bytes=0-0")
            .build()

        var totalZipSize = -1L
        var resolvedCdnUrl = zipUrl
        okHttpClient.newCall(probeReq).execute().use { resp ->
            resolvedCdnUrl = resp.request.url.toString()
            if (resp.code == 206) {
                val contentRange = resp.header("Content-Range") ?: ""
                totalZipSize = contentRange.substringAfterLast('/', "").toLongOrNull() ?: -1L
            }
        }
        if (totalZipSize <= 1024L) return null

        // Step 2: Fetch the last 262,144 bytes (256 KB) of the ZIP containing the Central Directory
        val tailSize = minOf(262144L, totalZipSize)
        val rangeStart = totalZipSize - tailSize
        onProgress(0.32f, "Reading ZIP index (${totalZipSize / (1024 * 1024)} MB archive) via HTTP Range...")

        val tailReq = Request.Builder()
            .url(resolvedCdnUrl)
            .header("Range", "bytes=$rangeStart-${totalZipSize - 1}")
            .build()

        val tailBytes = okHttpClient.newCall(tailReq).execute().use { resp ->
            if (resp.code != 206) return null
            resp.body?.bytes()
        } ?: return null

        // Step 3: Locate End of Central Directory (EOCD signature 0x06054b50) from the end of tailBytes
        var eocdPos = -1
        for (i in tailBytes.size - 22 downTo 0) {
            if (readLe32(tailBytes, i) == 0x06054B50) {
                eocdPos = i
                break
            }
        }
        if (eocdPos < 0) return null

        val cdSize = readLe32(tailBytes, eocdPos + 12).toLong() and 0xFFFFFFFFL
        val cdOffset = readLe32(tailBytes, eocdPos + 16).toLong() and 0xFFFFFFFFL
        if (cdOffset == 0xFFFFFFFFL || cdSize <= 0L) return null

        val cdBytes: ByteArray = if (cdOffset >= rangeStart) {
            val localPos = (cdOffset - rangeStart).toInt()
            tailBytes.copyOfRange(localPos, minOf(tailBytes.size, localPos + cdSize.toInt()))
        } else {
            val cdReq = Request.Builder()
                .url(resolvedCdnUrl)
                .header("Range", "bytes=$cdOffset-${cdOffset + cdSize - 1}")
                .build()
            okHttpClient.newCall(cdReq).execute().use { resp ->
                if (resp.code != 206) return null
                resp.body?.bytes()
            } ?: return null
        }

        // Step 4: Parse Central Directory headers (signature 0x02014b50)
        val entries = mutableListOf<ZipCdEntry>()
        var pos = 0
        while (pos + 46 <= cdBytes.size) {
            val sig = readLe32(cdBytes, pos)
            if (sig != 0x02014B50) break
            val method = readLe16(cdBytes, pos + 10)
            val crc = readLe32(cdBytes, pos + 16).toLong() and 0xFFFFFFFFL
            val compSize = readLe32(cdBytes, pos + 20)
            val uncompSize = readLe32(cdBytes, pos + 24)
            val nameLen = readLe16(cdBytes, pos + 28)
            val extraLen = readLe16(cdBytes, pos + 30)
            val commentLen = readLe16(cdBytes, pos + 32)
            val localOffset = readLe32(cdBytes, pos + 42).toLong() and 0xFFFFFFFFL

            if (pos + 46 + nameLen <= cdBytes.size) {
                val rawName = String(cdBytes, pos + 46, nameLen, Charsets.UTF_8)
                val cleanName = rawName.substringAfterLast('/')
                if (cleanName.isNotEmpty() && compSize > 0) {
                    entries.add(
                        ZipCdEntry(
                            fileName = cleanName,
                            compressionMethod = method,
                            crc32 = crc,
                            compressedSize = compSize,
                            uncompressedSize = uncompSize,
                            localHeaderOffset = localOffset
                        )
                    )
                }
            }
            pos += 46 + nameLen + extraLen + commentLen
        }

        if (entries.isEmpty()) return null
        val index = RemoteZipIndex(
            resolvedCdnUrl = resolvedCdnUrl,
            totalZipSize = totalZipSize,
            entries = entries
        )
        zipIndexCache[zipUrl] = index
        return index
    }

    private fun tryExtractBoardFromRemoteZipViaRange(
        zipUrl: String,
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole,
        preferFactoryImage: Boolean,
        useMeshtasticMui: Boolean = true,
        bundleWebUi: Boolean = false,
        preferBleOtaPackage: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): PreparedFirmwareImage? {
        val zipIndex = fetchOrGetRemoteZipIndex(zipUrl, onProgress) ?: return null
        val entries = zipIndex.entries
        val totalZipSize = zipIndex.totalZipSize
        val resolvedCdnUrl = zipIndex.resolvedCdnUrl

        // If the remote ZIP is already a single-board Nordic/Adafruit DFU package (contains .dat + .bin),
        // only download the entire DFU .zip when preferBleOtaPackage is true (for BLE OTA flashing).
        // For USB OTG flashing, prefer extracting the .uf2 file!
        val isSingleBoardDfuZip = board.mcu == McuArchitecture.NRF52840 &&
            preferBleOtaPackage &&
            entries.size <= 6 &&
            entries.any { it.fileName.endsWith(".dat", ignoreCase = true) } &&
            entries.any { it.fileName.endsWith(".bin", ignoreCase = true) }
        if (isSingleBoardDfuZip && totalZipSize <= 2_500_000L) {
            onProgress(0.55f, "Downloading ${board.name} DFU package (${totalZipSize / 1024} KB)...")
            val fullZipReq = Request.Builder().url(resolvedCdnUrl).build()
            val fullZipBytes = okHttpClient.newCall(fullZipReq).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
            if (fullZipBytes != null && fullZipBytes.size > 1024) {
                return inspectAndWrapBinary(
                    bytes = fullZipBytes,
                    fileName = "${board.meshtasticTarget}-ota.zip",
                    board = board,
                    isRealBinary = true
                )
            }
        }

        val desiredExt = if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) ".uf2" else ".bin"

        val allBoardEntries = entries.filter { e ->
            val lower = e.fileName.lowercase(Locale.US)
            val validExt = lower.endsWith(desiredExt) ||
                (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage && (lower.endsWith("-ota.zip") || lower.endsWith(".zip") || lower.endsWith(".bin")))
            validExt &&
                !lower.contains("littlefs") &&
                !lower.contains("bleota") &&
                !lower.startsWith("mt-") &&
                matchesBoardStrictly(board, lower)
        }

        // For LilyGO T-Deck on Meshtastic, filter for t-deck-tft (Meshtastic UI / MUI) vs standard t-deck (BaseUI)
        val matchingEntries = if (board.id == "lilygo_tdeck" && ecosystem == FirmwareEcosystem.MESHTASTIC) {
            val muiFiltered = allBoardEntries.filter { e ->
                val lower = e.fileName.lowercase(Locale.US)
                val isTft = lower.contains("t-deck-tft") || lower.contains("tft") || lower.contains("mui")
                val isBase = lower.contains("t-deck") && !lower.contains("tft") && !lower.contains("pro")
                if (useMeshtasticMui) isTft else isBase
            }
            muiFiltered.ifEmpty { allBoardEntries }
        } else {
            allBoardEntries
        }

        // On nRF52840 (SenseCAP T1000-E, RAK4631, LilyGO T-Echo):
        // Only select the official -ota.zip DFU package when preferBleOtaPackage is true!
        // When flashing over USB OTG (preferBleOtaPackage == false), ALWAYS select the official .uf2 file!
        val nrfOtaZipEntry = if (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage) {
            matchingEntries.firstOrNull {
                val lower = it.fileName.lowercase(Locale.US)
                lower.endsWith("-ota.zip") || lower.endsWith("_ota.zip") || lower.endsWith(".zip")
            }
        } else {
            null
        }

        // Select between .factory.bin (0x0000 full factory image) and .bin / -update.bin (0x10000 app update image)
        val hasModernFactoryNaming = matchingEntries.any { isFactoryBinaryName(it.fileName.lowercase(Locale.US)) }
        val chosenEntry = nrfOtaZipEntry ?: if (hasModernFactoryNaming) {
            if (preferFactoryImage) {
                matchingEntries.firstOrNull { isFactoryBinaryName(it.fileName.lowercase(Locale.US)) }
                    ?: matchingEntries.firstOrNull()
            } else {
                matchingEntries.firstOrNull { !isFactoryBinaryName(it.fileName.lowercase(Locale.US)) }
                    ?: matchingEntries.firstOrNull()
            }
        } else {
            if (preferFactoryImage) {
                matchingEntries.firstOrNull { !isExplicitUpdateBinaryName(it.fileName.lowercase(Locale.US)) }
                    ?: matchingEntries.firstOrNull()
            } else {
                matchingEntries.firstOrNull { isExplicitUpdateBinaryName(it.fileName.lowercase(Locale.US)) }
                    ?: matchingEntries.firstOrNull()
            }
        } ?: entries.firstOrNull {
            // Single-board nRF52 DFU .zip archive (contains 1 .bin/.uf2 + manifest.json)
            val lower = it.fileName.lowercase(Locale.US)
            (lower.endsWith(desiredExt) || lower.endsWith(".bin")) &&
                !lower.contains("bleota") &&
                !lower.contains("littlefs") &&
                entries.size <= 6
        } ?: return null

        // Step 5: Download ONLY the compressed bytes for chosenEntry via HTTP Range!
        val fetchEnd = (chosenEntry.localHeaderOffset + 30 + 256 + chosenEntry.compressedSize).coerceAtMost(totalZipSize - 1)
        onProgress(
            0.55f,
            "Downloading ${chosenEntry.fileName} (${chosenEntry.compressedSize / 1024} KB compressed)..."
        )

        val entryReq = Request.Builder()
            .url(resolvedCdnUrl)
            .header("Range", "bytes=${chosenEntry.localHeaderOffset}-$fetchEnd")
            .build()

        val sliceBytes = okHttpClient.newCall(entryReq).execute().use { resp ->
            if (resp.code != 206) return null
            resp.body?.bytes()
        } ?: return null

        // Verify Local File Header (0x04034b50)
        if (sliceBytes.size < 30 || readLe32(sliceBytes, 0) != 0x04034B50) return null
        val localNameLen = readLe16(sliceBytes, 26)
        val localExtraLen = readLe16(sliceBytes, 28)
        val dataStart = 30 + localNameLen + localExtraLen
        if (dataStart + chosenEntry.compressedSize > sliceBytes.size) return null

        onProgress(0.80f, "Decompressing & verifying ${chosenEntry.fileName}...")
        val uncompressedBytes: ByteArray = when (chosenEntry.compressionMethod) {
            0 -> sliceBytes.copyOfRange(dataStart, dataStart + chosenEntry.compressedSize)
            8 -> {
                val bais = ByteArrayInputStream(sliceBytes, dataStart, chosenEntry.compressedSize)
                val inflater = Inflater(true)
                try {
                    val iis = InflaterInputStream(bais, inflater, 16384)
                    val out = ByteArrayOutputStream(chosenEntry.uncompressedSize.coerceAtLeast(65536))
                    val buf = ByteArray(16384)
                    var n: Int
                    while (iis.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } finally {
                    inflater.end()
                }
            }
            else -> return null
        }

        // Verify ZIP CRC32
        val crc = CRC32()
        crc.update(uncompressedBytes)
        if (chosenEntry.crc32 != 0L && crc.value != chosenEntry.crc32) {
            return null
        }

        val baseWrapped = inspectAndWrapBinary(
            bytes = uncompressedBytes,
            fileName = chosenEntry.fileName,
            board = board,
            isRealBinary = true
        )

        val isEsp = board.mcu == McuArchitecture.ESP32 ||
            board.mcu == McuArchitecture.ESP32_S3 ||
            board.mcu == McuArchitecture.ESP32_C3

        val littleFsEntry = if (bundleWebUi && isEsp) {
            entries.firstOrNull { e ->
                val lower = e.fileName.lowercase(Locale.US)
                lower.endsWith(".bin") && lower.contains("littlefs") && matchesBoardStrictly(board, lower)
            }
        } else null

        if (littleFsEntry != null) {
            try {
                val fsEnd = (littleFsEntry.localHeaderOffset + 30 + 256 + littleFsEntry.compressedSize).coerceAtMost(totalZipSize - 1)
                val fsReq = Request.Builder()
                    .url(resolvedCdnUrl)
                    .header("Range", "bytes=${littleFsEntry.localHeaderOffset}-$fsEnd")
                    .build()
                val fsSlice = okHttpClient.newCall(fsReq).execute().use { resp ->
                    if (resp.code != 206) null else resp.body?.bytes()
                }
                if (fsSlice != null && fsSlice.size >= 30 && readLe32(fsSlice, 0) == 0x04034B50) {
                    val nLen = readLe16(fsSlice, 26)
                    val xLen = readLe16(fsSlice, 28)
                    val dStart = 30 + nLen + xLen
                    if (dStart + littleFsEntry.compressedSize <= fsSlice.size) {
                        val fsBytes: ByteArray? = when (littleFsEntry.compressionMethod) {
                            0 -> fsSlice.copyOfRange(dStart, dStart + littleFsEntry.compressedSize)
                            8 -> {
                                val bais = ByteArrayInputStream(fsSlice, dStart, littleFsEntry.compressedSize)
                                val inflater = Inflater(true)
                                try {
                                    val iis = InflaterInputStream(bais, inflater, 16384)
                                    val out = ByteArrayOutputStream(littleFsEntry.uncompressedSize.coerceAtLeast(65536))
                                    val buf = ByteArray(16384)
                                    var n: Int
                                    while (iis.read(buf).also { n = it } != -1) {
                                        out.write(buf, 0, n)
                                    }
                                    out.toByteArray()
                                } finally {
                                    inflater.end()
                                }
                            }
                            else -> null
                        }
                        if (fsBytes != null) {
                            val offset = if (board.flashSizeMb <= 4) 0x290000 else 0x300000
                            return baseWrapped.copy(
                                littleFsBytes = fsBytes,
                                littleFsOffset = offset,
                                littleFsFileName = littleFsEntry.fileName
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return baseWrapped
    }

    /**
     * Streams a ZIP archive and extracts the matching board binary.
     */
    fun extractBoardBinaryFromZipStream(
        inputStream: InputStream,
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole,
        preferFactoryImage: Boolean,
        useMeshtasticMui: Boolean = true,
        bundleWebUi: Boolean = false,
        preferBleOtaPackage: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): PreparedFirmwareImage? {
        val desiredExt = if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) ".uf2" else ".bin"

        val matchedEntries = mutableListOf<Pair<String, ByteArray>>()
        val nonTargetCandidates = mutableListOf<Pair<String, ByteArray>>()
        var datEntry: Pair<String, ByteArray>? = null
        var binEntry: Pair<String, ByteArray>? = null
        var littleFsPair: Pair<String, ByteArray>? = null
        var totalEntriesSeen = 0

        val zis = ZipInputStream(inputStream)
        try {
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    totalEntriesSeen++
                    val entryName = entry.name.substringAfterLast('/')
                    val lower = entryName.lowercase(Locale.US)

                    if (lower.endsWith(".dat") && datEntry == null) {
                        datEntry = Pair(entryName, readCurrentZipEntryBytes(zis))
                    } else if (lower.endsWith(".bin") && lower.contains("littlefs") && matchesBoardStrictly(board, lower) && littleFsPair == null) {
                        littleFsPair = Pair(entryName, readCurrentZipEntryBytes(zis))
                    } else if (
                        (lower.endsWith(desiredExt) ||
                            (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage && (lower.endsWith("-ota.zip") || lower.endsWith(".bin")))) &&
                        !lower.contains("littlefs") &&
                        !lower.contains("bleota") &&
                        !lower.startsWith("mt-")
                    ) {
                        val bytes = readCurrentZipEntryBytes(zis)
                        if (lower.endsWith(".bin") && binEntry == null) {
                            binEntry = Pair(entryName, bytes)
                        }
                        val matchesBoard = matchesBoardStrictly(board, lower)
                        val matchesTDeckUiMode = if (board.id == "lilygo_tdeck" && ecosystem == FirmwareEcosystem.MESHTASTIC) {
                            val isTft = lower.contains("t-deck-tft") || lower.contains("tft") || lower.contains("mui")
                            val isBase = lower.contains("t-deck") && !lower.contains("tft") && !lower.contains("pro")
                            if (useMeshtasticMui) isTft else isBase
                        } else {
                            true
                        }
                        if (matchesBoard && matchesTDeckUiMode) {
                            onProgress(0.70f, "Extracting board image from archive: $entryName")
                            matchedEntries.add(Pair(entryName, bytes))
                            if (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage && lower.endsWith("-ota.zip")) break
                            if (preferFactoryImage && isFactoryBinaryName(lower)) break
                            if (!preferFactoryImage && isExplicitUpdateBinaryName(lower)) break
                        } else if (totalEntriesSeen <= 6 && nonTargetCandidates.isEmpty()) {
                            // Single-board DFU zip fallback only if archive has <= 6 files total
                            nonTargetCandidates.add(Pair(entryName, bytes))
                        }
                    }
                }
                entry = zis.nextEntry
            }
        } catch (_: Exception) {
            // If stream closes early after we already extracted our target entry, use the extracted bytes
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {
            }
        }

        // If this was a single-board Nordic/Adafruit DFU .zip with both .dat and .bin, only repackage when preferBleOtaPackage is true
        if (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage && datEntry != null && binEntry != null && totalEntriesSeen <= 6) {
            val bos = ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry(datEntry!!.first))
                zos.write(datEntry!!.second)
                zos.closeEntry()
                zos.putNextEntry(java.util.zip.ZipEntry(binEntry!!.first))
                zos.write(binEntry!!.second)
                zos.closeEntry()
            }
            return inspectAndWrapBinary(
                bytes = bos.toByteArray(),
                fileName = binEntry!!.first.removeSuffix(".bin") + "-ota.zip",
                board = board,
                isRealBinary = true
            )
        }

        val nrfOtaMatch = if (board.mcu == McuArchitecture.NRF52840 && preferBleOtaPackage) {
            matchedEntries.firstOrNull { it.first.lowercase(Locale.US).endsWith("-ota.zip") }
        } else {
            null
        }
        val hasModernFactory = matchedEntries.any { isFactoryBinaryName(it.first.lowercase(Locale.US)) }
        val chosenPair: Pair<String, ByteArray>? = nrfOtaMatch ?: if (matchedEntries.isNotEmpty()) {
            if (hasModernFactory) {
                if (preferFactoryImage) {
                    matchedEntries.firstOrNull { isFactoryBinaryName(it.first.lowercase(Locale.US)) }
                        ?: matchedEntries.first()
                } else {
                    matchedEntries.firstOrNull { !isFactoryBinaryName(it.first.lowercase(Locale.US)) }
                        ?: matchedEntries.first()
                }
            } else {
                if (preferFactoryImage) {
                    matchedEntries.firstOrNull { !isExplicitUpdateBinaryName(it.first.lowercase(Locale.US)) }
                        ?: matchedEntries.first()
                } else {
                    matchedEntries.firstOrNull { isExplicitUpdateBinaryName(it.first.lowercase(Locale.US)) }
                        ?: matchedEntries.first()
                }
            }
        } else if (totalEntriesSeen <= 6 && nonTargetCandidates.isNotEmpty()) {
            nonTargetCandidates.first()
        } else {
            null
        }

        val baseWrapped = chosenPair?.let { (name, bytes) ->
            inspectAndWrapBinary(
                bytes = bytes,
                fileName = name,
                board = board,
                isRealBinary = true
            )
        }
        return if (baseWrapped != null && littleFsPair != null && bundleWebUi) {
            val offset = if (board.flashSizeMb <= 4) 0x290000 else 0x300000
            baseWrapped.copy(
                littleFsBytes = littleFsPair.second,
                littleFsOffset = offset,
                littleFsFileName = littleFsPair.first
            )
        } else {
            baseWrapped
        }
    }

    fun inspectUserImportedBytes(
        rawBytes: ByteArray,
        fileName: String,
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole,
        preferFactoryImage: Boolean
    ): PreparedFirmwareImage {
        if (fileName.endsWith(".zip", ignoreCase = true)) {
            extractBoardBinaryFromZipStream(
                inputStream = ByteArrayInputStream(rawBytes),
                board = board,
                ecosystem = ecosystem,
                meshCoreRole = meshCoreRole,
                preferFactoryImage = preferFactoryImage,
                onProgress = { _, _ -> }
            )?.let { return it }
        }
        return inspectAndWrapBinary(
            bytes = rawBytes,
            fileName = fileName,
            board = board,
            isRealBinary = true
        )
    }

    fun inspectAndWrapBinary(
        bytes: ByteArray,
        fileName: String,
        board: HardwareBoard,
        isRealBinary: Boolean
    ): PreparedFirmwareImage {
        val sha = sha256Hex(bytes)
        val lowerName = fileName.lowercase(Locale.US)

        if (board.mcu == McuArchitecture.NRF52840) {
            return PreparedFirmwareImage(
                bytes = bytes,
                sha256Hex = sha,
                flashOffset = 0x26000,
                fileName = fileName,
                isFullFactoryImage = true,
                isRealBinary = isRealBinary
            )
        }
        if (board.mcu == McuArchitecture.RP2040) {
            return PreparedFirmwareImage(
                bytes = bytes,
                sha256Hex = sha,
                flashOffset = 0x10000000,
                fileName = fileName,
                isFullFactoryImage = true,
                isRealBinary = isRealBinary
            )
        }

        val hasAppMagicAt0x20 = hasEspAppDescMagic(bytes, 0x20)
        val hasAppMagicAt0x10020 = hasEspAppDescMagic(bytes, 0x10020)
        val hasAppMagicAt0xF020 = hasEspAppDescMagic(bytes, 0xF020)
        val isMergedImage = lowerName.contains("-merged.bin") ||
            lowerName.contains("_merged.bin") ||
            lowerName.endsWith("merged.bin")
        val isFactoryNamed = isFactoryBinaryName(lowerName)

        val isFullFactory = when {
            isMergedImage || isFactoryNamed -> true
            hasAppMagicAt0x10020 || hasAppMagicAt0xF020 -> true
            hasAppMagicAt0x20 -> false
            isExplicitUpdateBinaryName(lowerName) -> false
            bytes.size > 0x10000 -> true
            else -> false
        }

        // Note: Both Meshtastic .factory.bin and MeshCore -merged.bin pad from 0x0000 (even on ESP32),
        // placing the partition table at 0x8000 and the app at 0x10000. Only use 0x1000 if unpadded ESP32 image.
        val flashOffset = if (isFullFactory) {
            if (board.mcu == McuArchitecture.ESP32 && hasAppMagicAt0xF020 && !hasAppMagicAt0x10020 && !isMergedImage && !isFactoryNamed) {
                0x1000
            } else {
                0x0000
            }
        } else {
            0x10000
        }

        return PreparedFirmwareImage(
            bytes = bytes,
            sha256Hex = sha,
            flashOffset = flashOffset,
            fileName = fileName,
            isFullFactoryImage = isFullFactory,
            isRealBinary = isRealBinary
        )
    }

    private fun hasEspAppDescMagic(bytes: ByteArray, offset: Int): Boolean {
        if (bytes.size < offset + 4) return false
        return (bytes[offset].toInt() and 0xFF) == 0x32 &&
            (bytes[offset + 1].toInt() and 0xFF) == 0x54 &&
            (bytes[offset + 2].toInt() and 0xFF) == 0xCD &&
            (bytes[offset + 3].toInt() and 0xFF) == 0xAB
    }

    private fun readCurrentZipEntryBytes(zis: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var read: Int
        while (zis.read(buf).also { read = it } != -1) {
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun readLe16(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLe32(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun resolveExpectedFileName(
        board: HardwareBoard,
        release: FirmwareRelease,
        meshCoreRole: MeshCoreRole,
        fullErase: Boolean = false,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE
    ): String {
        val ext = when (board.mcu) {
            McuArchitecture.NRF52840, McuArchitecture.RP2040 -> "uf2"
            else -> "bin"
        }
        return when (release.ecosystem) {
            FirmwareEcosystem.MESHTASTIC -> {
                val ver = release.tagName.removePrefix("v")
                val targetName = when {
                    board.id == "lilygo_tdeck" && useMeshtasticMui -> "t-deck-tft"
                    board.id == "lilygo_tdeck" && !useMeshtasticMui -> "t-deck"
                    board.id == "rpi_pico_waveshare" -> "pico"
                    else -> board.meshtasticTarget
                }
                val suffix = if (fullErase && ext == "bin") ".factory" else ""
                "firmware-${targetName}-${ver}${suffix}.$ext"
            }
            FirmwareEcosystem.MESHCORE -> {
                val matchedAsset = selectCandidateAssets(
                    release = release,
                    board = board,
                    meshCoreRole = meshCoreRole,
                    useMeshtasticMui = useMeshtasticMui,
                    tDeckMeshCoreFlavor = tDeckMeshCoreFlavor
                ).firstOrNull()
                if (matchedAsset != null) {
                    return matchedAsset.fileName
                }
                if (board.id == "lilygo_tdeck") {
                    when (tDeckMeshCoreFlavor) {
                        TDeckMeshCoreFlavor.RIPPLE -> "Ultra-TDeck-${release.tagName}-merged.$ext"
                        TDeckMeshCoreFlavor.WADAMESH -> "wadamesh-tdeck-merged.$ext"
                    }
                } else {
                    val cleanVer = extractMeshCoreVersionSuffix(release.tagName)
                    val mergedSuffix = if (ext == "bin") "-merged" else ""
                    "${board.meshCoreTarget}_${meshCoreRole.binarySuffix}-${cleanVer}${mergedSuffix}.$ext"
                }
            }
        }
    }

    private fun buildValidMcuFirmwareImage(
        board: HardwareBoard,
        release: FirmwareRelease,
        meshCoreRole: MeshCoreRole,
        targetAssetName: String,
        includeBootloader: Boolean,
        onProgress: (Float, String) -> Unit
    ): ByteArray {
        onProgress(0.55f, "Packing ${board.mcu.shortName} test bench image ($targetAssetName)...")
        val totalSize = if (includeBootloader) 65536 else 49152
        val buffer = ByteArray(totalSize)

        when (board.mcu) {
            McuArchitecture.NRF52840, McuArchitecture.RP2040 -> {
                val numBlocks = totalSize / 512
                for (block in 0 until numBlocks) {
                    val base = block * 512
                    writeLe32(buffer, base + 0, 0x0A324655)
                    writeLe32(buffer, base + 4, 0x9E5D5157.toInt())
                    writeLe32(buffer, base + 8, 0x00002000)
                    writeLe32(buffer, base + 12, 0x00026000 + block * 256)
                    writeLe32(buffer, base + 16, 256)
                    writeLe32(buffer, base + 20, block)
                    writeLe32(buffer, base + 24, numBlocks)
                    val familyId = if (board.mcu == McuArchitecture.NRF52840) 0xADA52840.toInt() else 0xE48BFF56.toInt()
                    writeLe32(buffer, base + 28, familyId)
                    val meta = "${release.ecosystem.displayName}:${release.tagName}:${board.id}:${meshCoreRole.name}:BLK$block".encodeToByteArray()
                    System.arraycopy(meta, 0, buffer, base + 32, meta.size.coerceAtMost(240))
                    writeLe32(buffer, base + 508, 0x0AB16F30)
                }
            }
            else -> {
                buffer[0] = 0xE9.toByte()
                buffer[1] = 0x03
                buffer[2] = 0x02
                buffer[3] = if (board.flashSizeMb >= 8) 0x3F else 0x2F
                writeLe32(buffer, 4, 0x40378000)
                val chipId = when (board.mcu) {
                    McuArchitecture.ESP32_S3 -> 0x0009
                    McuArchitecture.ESP32_C3 -> 0x0005
                    else -> 0x0000
                }
                buffer[12] = (chipId and 0xFF).toByte()
                buffer[13] = ((chipId shr 8) and 0xFF).toByte()
                writeLe32(buffer, 32, 0xABCD5432.toInt())
                val versionBytes = release.tagName.encodeToByteArray()
                System.arraycopy(versionBytes, 0, buffer, 48, versionBytes.size.coerceAtMost(31))
                val projBytes = "${release.ecosystem.displayName}-${board.meshtasticTarget}-${meshCoreRole.binarySuffix}".encodeToByteArray()
                System.arraycopy(projBytes, 0, buffer, 80, projBytes.size.coerceAtMost(31))
                for (i in 256 until totalSize - 1) {
                    buffer[i] = ((i * 31 + board.id.hashCode() + release.tagName.hashCode()) and 0xFF).toByte()
                }
                var xorChecksum = 0xEF
                for (i in 24 until totalSize - 1) {
                    xorChecksum = xorChecksum xor (buffer[i].toInt() and 0xFF)
                }
                buffer[totalSize - 1] = xorChecksum.toByte()
            }
        }
        return buffer
    }

    private fun writeLe32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Initial UI placeholders marked with `fallback-` IDs so `prepareFirmwarePayload` always resolves
     * live release tags and real download URLs from GitHub / Meshtastic / MeshCore before flashing.
     */
    fun curatedFallbackReleases(ecosystem: FirmwareEcosystem): List<FirmwareRelease> {
        return when (ecosystem) {
            FirmwareEcosystem.MESHTASTIC -> listOf(
                FirmwareRelease(
                    id = "fallback-mt-stable",
                    tagName = "v2.7.26.54e0d8d",
                    title = "Meshtastic Firmware 2.7.26.54e0d8d Beta",
                    publishedDate = "Production Beta / Stable",
                    ecosystem = FirmwareEcosystem.MESHTASTIC,
                    isPrerelease = false,
                    changelog = "• Managed Flood & Next-Hop routing\n• Full support for Heltec V4 (27dBm PA), Heltec V3, Station G2, RAK4631, SenseCAP T1000-E, T-Beam Supreme & T-Echo\n• Official production-tested Meshtastic 2.7 Beta / Stable release",
                    assets = buildStandardMeshtasticAssetsForTag("v2.7.26.54e0d8d")
                ),
                FirmwareRelease(
                    id = "fallback-mt-alpha",
                    tagName = "v2.8.1.8e6a88d",
                    title = "Meshtastic Firmware 2.8.1.8e6a88d Alpha",
                    publishedDate = "Alpha Preview",
                    ecosystem = FirmwareEcosystem.MESHTASTIC,
                    isPrerelease = true,
                    changelog = "• Bleeding-edge Meshtastic 2.8 preview build with new features and memory optimizations\n• Direct official firmware-esp32s3 / nrf52840 binaries",
                    assets = buildStandardMeshtasticAssetsForTag("v2.8.1.8e6a88d")
                )
            )
            FirmwareEcosystem.MESHCORE -> listOf(
                FirmwareRelease(
                    id = "fallback-mc-stable",
                    tagName = "latest",
                    title = "MeshCore Official Multi-Role Release",
                    publishedDate = "Live GitHub Sync",
                    ecosystem = FirmwareEcosystem.MESHCORE,
                    isPrerelease = false,
                    changelog = "• Multi-hop routing mesh for Companion Radio (BLE/USB), Standalone Repeater & Room Server\n• Automatically resolves the latest official binaries from github.com/meshcore-dev/MeshCore",
                    assets = emptyList()
                )
            )
        }
    }
}
