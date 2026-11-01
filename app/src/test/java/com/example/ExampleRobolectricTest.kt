package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BoardCatalog
import com.example.model.FirmwareAsset
import com.example.model.FirmwareEcosystem
import com.example.model.FirmwareRelease
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.TDeckMeshCoreFlavor
import com.example.network.GitHubFirmwareRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify station g2 usb matching and zip binary extraction`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mesh Installer", appName)

        val stationG2 = BoardCatalog.allBoards.first { it.id == "station_g2" }
        assertEquals(McuArchitecture.ESP32_S3, stationG2.mcu)
        assertEquals(16, stationG2.flashSizeMb)

        // Ensure generic Espressif USB JTAG/serial debug unit (VID 12346 / 0x303A) preserves Station G2 selection
        val matched = BoardCatalog.matchByUsbVidPid(
            vendorId = 12346,
            productId = 4097,
            productName = "USB JTAG/serial debug unit",
            currentlySelectedBoard = stationG2
        )
        assertEquals("station_g2", matched?.id)

        val repo = GitHubFirmwareRepository()

        // Create an in-memory Meshtastic ESP32-S3 release ZIP containing Station G2 factory & update binaries
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                // 1. Update binary (has ESP_APP_DESC_MAGIC 0xABCD5432 at offset 0x20 -> flashes at 0x10000)
                zos.putNextEntry(ZipEntry("firmware-station-g2-2.6.4.b89355f-update.bin"))
                val updateBin = ByteArray(1024)
                updateBin[0] = 0xE9.toByte()
                updateBin[0x20] = 0x32
                updateBin[0x21] = 0x54
                updateBin[0x22] = 0xCD.toByte()
                updateBin[0x23] = 0xAB.toByte()
                zos.write(updateBin)
                zos.closeEntry()

                // 2. Full Factory binary (has bootloader at 0x0000 and app at 0x10000 -> flashes at 0x0000)
                zos.putNextEntry(ZipEntry("firmware-station-g2-2.6.4.b89355f.bin"))
                val factoryBin = ByteArray(0x10100)
                factoryBin[0] = 0xE9.toByte()
                factoryBin[0x10020] = 0x32
                factoryBin[0x10021] = 0x54
                factoryBin[0x10022] = 0xCD.toByte()
                factoryBin[0x10023] = 0xAB.toByte()
                zos.write(factoryBin)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val extractedUpdate = repo.extractBoardBinaryFromZipStream(
            inputStream = ByteArrayInputStream(zipBytes),
            board = stationG2,
            ecosystem = FirmwareEcosystem.MESHTASTIC,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            preferFactoryImage = false,
            onProgress = { _, _ -> }
        )
        assertNotNull(extractedUpdate)
        assertEquals("firmware-station-g2-2.6.4.b89355f-update.bin", extractedUpdate!!.fileName)
        assertEquals(0x10000, extractedUpdate.flashOffset)
        assertFalse(extractedUpdate.isFullFactoryImage)
        assertTrue(extractedUpdate.isRealBinary)

        val extractedFactory = repo.extractBoardBinaryFromZipStream(
            inputStream = ByteArrayInputStream(zipBytes),
            board = stationG2,
            ecosystem = FirmwareEcosystem.MESHTASTIC,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            preferFactoryImage = true,
            onProgress = { _, _ -> }
        )
        assertNotNull(extractedFactory)
        assertEquals("firmware-station-g2-2.6.4.b89355f.bin", extractedFactory!!.fileName)
        assertEquals(0x0000, extractedFactory.flashOffset)
        assertTrue(extractedFactory.isFullFactoryImage)

        // Verify modern Meshtastic 2.8 ZIP convention (.factory.bin @ 0x0000 vs .bin @ 0x10000 without "-update" in name)
        // and verify that debug-elfs-esp32s3-*.zip is strictly rejected in selectCandidateAssets
        val mt28Release = FirmwareRelease(
            id = "mt-2.8.1",
            tagName = "v2.8.1.8e6a88d",
            title = "Meshtastic 2.8.1",
            publishedDate = "2026-04-10",
            ecosystem = FirmwareEcosystem.MESHTASTIC,
            isPrerelease = false,
            changelog = "Meshtastic 2.8.1",
            assets = listOf(
                FirmwareAsset("debug-elfs-esp32s3-2.8.1.8e6a88d.zip", "https://github.com/meshtastic/firmware/releases/download/v2.8.1.8e6a88d/debug-elfs-esp32s3-2.8.1.8e6a88d.zip", 1_374_631_960L),
                FirmwareAsset("firmware-esp32s3-2.8.1.8e6a88d.zip", "https://github.com/meshtastic/firmware/releases/download/v2.8.1.8e6a88d/firmware-esp32s3-2.8.1.8e6a88d.zip", 216_586_215L)
            )
        )
        val g2MtCandidates = repo.selectCandidateAssets(
            release = mt28Release,
            board = stationG2,
            meshCoreRole = MeshCoreRole.COMPANION_BLE
        )
        assertEquals(1, g2MtCandidates.size)
        assertEquals("firmware-esp32s3-2.8.1.8e6a88d.zip", g2MtCandidates.first().fileName)

        val mt28ZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                // Put littlefs and app-only .bin BEFORE .factory.bin to ensure .factory.bin is still chosen when preferFactoryImage=true
                zos.putNextEntry(ZipEntry("littlefs-station-g2-2.8.1.8e6a88d.bin"))
                zos.write(ByteArray(512))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("firmware-station-g2-2.8.1.8e6a88d.bin"))
                val appBin = ByteArray(1024)
                appBin[0] = 0xE9.toByte()
                appBin[0x20] = 0x32
                appBin[0x21] = 0x54
                appBin[0x22] = 0xCD.toByte()
                appBin[0x23] = 0xAB.toByte()
                zos.write(appBin)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("firmware-station-g2-2.8.1.8e6a88d.factory.bin"))
                val fullFactoryBin = ByteArray(0x10100)
                fullFactoryBin[0] = 0xE9.toByte()
                fullFactoryBin[0x10020] = 0x32
                fullFactoryBin[0x10021] = 0x54
                fullFactoryBin[0x10022] = 0xCD.toByte()
                fullFactoryBin[0x10023] = 0xAB.toByte()
                zos.write(fullFactoryBin)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val mt28Factory = repo.extractBoardBinaryFromZipStream(
            inputStream = ByteArrayInputStream(mt28ZipBytes),
            board = stationG2,
            ecosystem = FirmwareEcosystem.MESHTASTIC,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            preferFactoryImage = true,
            onProgress = { _, _ -> }
        )
        assertNotNull(mt28Factory)
        assertEquals("firmware-station-g2-2.8.1.8e6a88d.factory.bin", mt28Factory!!.fileName)
        assertEquals(0x0000, mt28Factory.flashOffset)
        assertTrue(mt28Factory.isFullFactoryImage)

        val mt28Update = repo.extractBoardBinaryFromZipStream(
            inputStream = ByteArrayInputStream(mt28ZipBytes),
            board = stationG2,
            ecosystem = FirmwareEcosystem.MESHTASTIC,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            preferFactoryImage = false,
            onProgress = { _, _ -> }
        )
        assertNotNull(mt28Update)
        assertEquals("firmware-station-g2-2.8.1.8e6a88d.bin", mt28Update!!.fileName)
        assertEquals(0x10000, mt28Update.flashOffset)
        assertFalse(mt28Update.isFullFactoryImage)

        // Verify LilyGO T-Deck Meshtastic UI (MUI / t-deck-tft) vs BaseUI (t-deck) & MeshCore (Ripple vs WadaMesh)
        val tDeck = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" }
        val mtRelease = repo.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
        val muiFileName = repo.resolveExpectedFileName(
            board = tDeck,
            release = mtRelease,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            fullErase = true,
            useMeshtasticMui = true
        )
        assertTrue(muiFileName.contains("t-deck-tft") && muiFileName.endsWith(".factory.bin"))

        val baseUiFileName = repo.resolveExpectedFileName(
            board = tDeck,
            release = mtRelease,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            fullErase = true,
            useMeshtasticMui = false
        )
        assertTrue(baseUiFileName.contains("firmware-t-deck-") && !baseUiFileName.contains("tft"))

        // Verify WadaMesh T-Deck asset selection rejects tdeck-max and tdeck-pro and selects wadamesh-tdeck-merged.bin @ 0x0000
        val wadaRelease = FirmwareRelease(
            id = "wada-beta-101",
            tagName = "beta_101",
            title = "WadaMesh beta_101",
            publishedDate = "2026-04-06",
            ecosystem = FirmwareEcosystem.MESHCORE,
            isPrerelease = false,
            changelog = "WadaMesh release",
            assets = listOf(
                FirmwareAsset("manifest-tdeck.json", "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/manifest-tdeck.json", 500L),
                FirmwareAsset("wadamesh-tdeck-max-merged.bin", "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck-max-merged.bin", 3400000L),
                FirmwareAsset("wadamesh-tdeck-pro.bin", "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck-pro.bin", 3300000L),
                FirmwareAsset("wadamesh-tdeck.bin", "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck.bin", 3351184L),
                FirmwareAsset("wadamesh-tdeck-merged.bin", "https://github.com/ALLFATHER-BV/wadamesh/releases/download/beta_101/wadamesh-tdeck-merged.bin", 3417680L)
            )
        )
        val wadaCandidates = repo.selectCandidateAssets(
            release = wadaRelease,
            board = tDeck,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.WADAMESH
        )
        assertTrue(wadaCandidates.isNotEmpty())
        assertEquals("wadamesh-tdeck-merged.bin", wadaCandidates.first().fileName)
        assertTrue(wadaCandidates.none { it.fileName.contains("tdeck-max") || it.fileName.contains("tdeck-pro") })

        val wrappedMerged = repo.inspectAndWrapBinary(
            bytes = ByteArray(4096) { 0xE9.toByte() },
            fileName = "wadamesh-tdeck-merged.bin",
            board = tDeck,
            isRealBinary = true
        )
        assertEquals(0x0000, wrappedMerged.flashOffset)
        assertTrue(wrappedMerged.isFullFactoryImage)

        // Verify Ripple Ultra UI T-Deck asset selection rejects room_server / repeater and selects Ultra-TDeck-v12.0-merged.bin @ 0x0000
        val rippleRelease = FirmwareRelease(
            id = "ripple-v12",
            tagName = "v12.0",
            title = "Ripple Ultra T-Deck v12.0",
            publishedDate = "Official",
            ecosystem = FirmwareEcosystem.MESHCORE,
            isPrerelease = false,
            changelog = "Ripple Ultra UI",
            assets = listOf(
                FirmwareAsset("LilyGo_TDeck_room_server.bin", "https://github.com/meshcore-dev/MeshCore/releases/download/v1.15.0/LilyGo_TDeck_room_server.bin", 1800000L),
                FirmwareAsset("LilyGo_TDeck_repeater.bin", "https://github.com/meshcore-dev/MeshCore/releases/download/v1.15.0/LilyGo_TDeck_repeater.bin", 1800000L),
                FirmwareAsset("Ultra-TDeck-SD-v12.0-merged.bin", "https://flasher.meshcore.io/releases/download/t-deck-plus-v12.0/Ultra-TDeck-SD-v12.0-merged.bin?repo=github-ripple", 4108656L),
                FirmwareAsset("Ultra-TDeck-v12.0-merged.bin", "https://flasher.meshcore.io/releases/download/t-deck-plus-v12.0/Ultra-TDeck-v12.0-merged.bin?repo=github-ripple", 4108656L)
            )
        )
        val rippleCandidates = repo.selectCandidateAssets(
            release = rippleRelease,
            board = tDeck,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE
        )
        assertTrue(rippleCandidates.isNotEmpty())
        assertEquals("Ultra-TDeck-v12.0-merged.bin", rippleCandidates.first().fileName)
        assertTrue(rippleCandidates.none { it.fileName.contains("room_server") || it.fileName.contains("repeater") })

        // Verify non-T-Deck MeshCore role release merging (companion-v1.18.0 + repeater-v1.18.0 + room-server-v1.18.0 -> v1.18.0)
        // and strict role selection for Companion BLE, Companion USB, Repeater, and Room Server across non-T-Deck boards
        val rawRoleReleases = listOf(
            FirmwareRelease(
                id = "mc-room-1.18.0",
                tagName = "room-server-v1.18.0",
                title = "MeshCore Room Server v1.18.0",
                publishedDate = "2026-04-12",
                ecosystem = FirmwareEcosystem.MESHCORE,
                isPrerelease = false,
                changelog = "Room Server release",
                assets = listOf(
                    FirmwareAsset("Heltec_v3_room_server-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v3_room_server-v1.18.0-81dbae5-merged.bin", 1900000L),
                    FirmwareAsset("Heltec_v4_room_server-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v4_room_server-v1.18.0-81dbae5-merged.bin", 1900000L),
                    FirmwareAsset("RAK_4631_room_server-v1.18.0-81dbae5.uf2", "https://example.com/RAK_4631_room_server-v1.18.0-81dbae5.uf2", 750000L),
                    FirmwareAsset("Station_G2_room_server-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G2_room_server-v1.18.0-81dbae5-merged.bin", 1900000L),
                    FirmwareAsset("Station_G3_room_server-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G3_room_server-v1.18.0-81dbae5-merged.bin", 1900000L),
                    FirmwareAsset("LilyGo_T-Echo_room_server-v1.18.0-81dbae5.uf2", "https://example.com/LilyGo_T-Echo_room_server-v1.18.0-81dbae5.uf2", 750000L),
                    FirmwareAsset("LilyGo_T-Echo-Lite_room_server-v1.18.0-81dbae5.uf2", "https://example.com/LilyGo_T-Echo-Lite_room_server-v1.18.0-81dbae5.uf2", 750000L)
                )
            ),
            FirmwareRelease(
                id = "mc-rep-1.18.0",
                tagName = "repeater-v1.18.0",
                title = "MeshCore Repeater v1.18.0",
                publishedDate = "2026-04-12",
                ecosystem = FirmwareEcosystem.MESHCORE,
                isPrerelease = false,
                changelog = "Repeater release",
                assets = listOf(
                    FirmwareAsset("Heltec_v3_repeater-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v3_repeater-v1.18.0-81dbae5-merged.bin", 1850000L),
                    FirmwareAsset("Heltec_v4_repeater-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v4_repeater-v1.18.0-81dbae5-merged.bin", 1850000L),
                    FirmwareAsset("RAK_4631_repeater-v1.18.0-81dbae5.uf2", "https://example.com/RAK_4631_repeater-v1.18.0-81dbae5.uf2", 740000L),
                    FirmwareAsset("Station_G2_repeater-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G2_repeater-v1.18.0-81dbae5-merged.bin", 1850000L),
                    FirmwareAsset("Station_G3_repeater-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G3_repeater-v1.18.0-81dbae5-merged.bin", 1850000L),
                    FirmwareAsset("LilyGo_T-Echo_repeater-v1.18.0-81dbae5.uf2", "https://example.com/LilyGo_T-Echo_repeater-v1.18.0-81dbae5.uf2", 740000L)
                )
            ),
            FirmwareRelease(
                id = "mc-comp-1.18.0",
                tagName = "companion-v1.18.0",
                title = "MeshCore Companion v1.18.0",
                publishedDate = "2026-04-12",
                ecosystem = FirmwareEcosystem.MESHCORE,
                isPrerelease = false,
                changelog = "Companion release",
                assets = listOf(
                    FirmwareAsset("Heltec_v3_companion_radio_ble-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v3_companion_radio_ble-v1.18.0-81dbae5-merged.bin", 1950000L),
                    FirmwareAsset("Heltec_v3_companion_radio_usb-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v3_companion_radio_usb-v1.18.0-81dbae5-merged.bin", 1920000L),
                    FirmwareAsset("Heltec_v4_companion_radio_ble-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v4_companion_radio_ble-v1.18.0-81dbae5-merged.bin", 1950000L),
                    FirmwareAsset("Heltec_v4_companion_radio_usb-v1.18.0-81dbae5-merged.bin", "https://example.com/Heltec_v4_companion_radio_usb-v1.18.0-81dbae5-merged.bin", 1920000L),
                    FirmwareAsset("RAK_4631_companion_radio_ble-v1.18.0-81dbae5.uf2", "https://example.com/RAK_4631_companion_radio_ble-v1.18.0-81dbae5.uf2", 780000L),
                    FirmwareAsset("RAK_4631_companion_radio_usb-v1.18.0-81dbae5.uf2", "https://example.com/RAK_4631_companion_radio_usb-v1.18.0-81dbae5.uf2", 770000L),
                    FirmwareAsset("Station_G2_companion_radio_ble-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G2_companion_radio_ble-v1.18.0-81dbae5-merged.bin", 1950000L),
                    FirmwareAsset("Station_G2_companion_radio_usb-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G2_companion_radio_usb-v1.18.0-81dbae5-merged.bin", 1920000L),
                    FirmwareAsset("Station_G3_companion_radio_ble-v1.18.0-81dbae5-merged.bin", "https://example.com/Station_G3_companion_radio_ble-v1.18.0-81dbae5-merged.bin", 1950000L),
                    FirmwareAsset("LilyGo_T-Echo_companion_radio_ble-v1.18.0-81dbae5.uf2", "https://example.com/LilyGo_T-Echo_companion_radio_ble-v1.18.0-81dbae5.uf2", 780000L),
                    FirmwareAsset("LilyGo_T-Echo-Lite_companion_radio_ble-v1.18.0-81dbae5.uf2", "https://example.com/LilyGo_T-Echo-Lite_companion_radio_ble-v1.18.0-81dbae5.uf2", 780000L)
                )
            )
        )

        val mergedReleases = repo.mergeMeshCoreRoleReleasesByVersion(rawRoleReleases)
        assertEquals(1, mergedReleases.size)
        val unifiedRelease = mergedReleases.first()
        assertEquals("v1.18.0", unifiedRelease.tagName)

        val heltecV3 = BoardCatalog.allBoards.first { it.id == "heltec_v3" }
        val heltecV4 = BoardCatalog.allBoards.first { it.id == "heltec_v4" }
        val rak4631 = BoardCatalog.allBoards.first { it.id == "rak4631" }
        val tEcho = BoardCatalog.allBoards.first { it.id == "lilygo_techo" }

        // Heltec V3: test all 4 roles
        assertEquals(
            "Heltec_v3_companion_radio_ble-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV3, MeshCoreRole.COMPANION_BLE).first().fileName
        )
        assertEquals(
            "Heltec_v3_companion_radio_usb-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV3, MeshCoreRole.COMPANION_USB).first().fileName
        )
        assertEquals(
            "Heltec_v3_repeater-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV3, MeshCoreRole.REPEATER).first().fileName
        )
        assertEquals(
            "Heltec_v3_room_server-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV3, MeshCoreRole.ROOM_SERVER).first().fileName
        )

        // Heltec V4: test all 4 MeshCore roles + Meshtastic expected filename
        assertEquals(
            "Heltec_v4_companion_radio_ble-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV4, MeshCoreRole.COMPANION_BLE).first().fileName
        )
        assertEquals(
            "Heltec_v4_companion_radio_usb-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV4, MeshCoreRole.COMPANION_USB).first().fileName
        )
        assertEquals(
            "Heltec_v4_repeater-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV4, MeshCoreRole.REPEATER).first().fileName
        )
        assertEquals(
            "Heltec_v4_room_server-v1.18.0-81dbae5-merged.bin",
            repo.selectCandidateAssets(unifiedRelease, heltecV4, MeshCoreRole.ROOM_SERVER).first().fileName
        )
        val v4MtName = repo.resolveExpectedFileName(
            board = heltecV4,
            release = mtRelease,
            meshCoreRole = MeshCoreRole.COMPANION_BLE,
            fullErase = true
        )
        assertTrue(v4MtName.contains("firmware-heltec-v4-") && v4MtName.endsWith(".factory.bin"))

        // RAK4631: test all 4 roles
        assertEquals(
            "RAK_4631_companion_radio_ble-v1.18.0-81dbae5.uf2",
            repo.selectCandidateAssets(unifiedRelease, rak4631, MeshCoreRole.COMPANION_BLE).first().fileName
        )
        assertEquals(
            "RAK_4631_companion_radio_usb-v1.18.0-81dbae5.uf2",
            repo.selectCandidateAssets(unifiedRelease, rak4631, MeshCoreRole.COMPANION_USB).first().fileName
        )
        assertEquals(
            "RAK_4631_repeater-v1.18.0-81dbae5.uf2",
            repo.selectCandidateAssets(unifiedRelease, rak4631, MeshCoreRole.REPEATER).first().fileName
        )
        assertEquals(
            "RAK_4631_room_server-v1.18.0-81dbae5.uf2",
            repo.selectCandidateAssets(unifiedRelease, rak4631, MeshCoreRole.ROOM_SERVER).first().fileName
        )

        // Station G2: ensure it never selects Station_G3 and strictly obeys role
        val g2Companion = repo.selectCandidateAssets(unifiedRelease, stationG2, MeshCoreRole.COMPANION_BLE)
        assertEquals("Station_G2_companion_radio_ble-v1.18.0-81dbae5-merged.bin", g2Companion.first().fileName)
        assertTrue(g2Companion.none { it.fileName.contains("Station_G3") || it.fileName.contains("room_server") || it.fileName.contains("repeater") })

        val g2RoomServer = repo.selectCandidateAssets(unifiedRelease, stationG2, MeshCoreRole.ROOM_SERVER)
        assertEquals("Station_G2_room_server-v1.18.0-81dbae5-merged.bin", g2RoomServer.first().fileName)
        assertTrue(g2RoomServer.none { it.fileName.contains("Station_G3") || it.fileName.contains("companion") || it.fileName.contains("repeater") })

        // LilyGO T-Echo: ensure it never selects T-Echo-Lite and strictly obeys role
        val tEchoCompanion = repo.selectCandidateAssets(unifiedRelease, tEcho, MeshCoreRole.COMPANION_BLE)
        assertEquals("LilyGo_T-Echo_companion_radio_ble-v1.18.0-81dbae5.uf2", tEchoCompanion.first().fileName)
        assertTrue(tEchoCompanion.none { it.fileName.contains("Lite") || it.fileName.contains("room_server") })

        val tEchoRepeater = repo.selectCandidateAssets(unifiedRelease, tEcho, MeshCoreRole.REPEATER)
        assertEquals("LilyGo_T-Echo_repeater-v1.18.0-81dbae5.uf2", tEchoRepeater.first().fileName)
        assertTrue(tEchoRepeater.none { it.fileName.contains("Lite") || it.fileName.contains("companion") })

        // Verify OfflineFirmwareVaultManager device selection, spec generation (4 for T-Deck + 5 per standard board), and disk persistence
        val vault = com.example.storage.OfflineFirmwareVaultManager(context)
        assertFalse(vault.hasCompletedOnboarding())
        vault.saveMyDeviceIds(setOf("lilygo_tdeck", "station_g2"))
        assertEquals(setOf("lilygo_tdeck", "station_g2"), vault.getMyDeviceIds())

        val specs = vault.buildDownloadSpecsForBoards(setOf("lilygo_tdeck", "station_g2"))
        // 5 for T-Deck (MUI Factory, MUI Update, BaseUI, Ripple, WadaMesh) + 6 for Station G2 (Meshtastic Factory + Update + 4 MeshCore roles) = 11 specs
        assertEquals(11, specs.size)

        kotlinx.coroutines.runBlocking<Unit> {
            val g2RoomSpec = specs.first { it.board.id == "station_g2" && it.meshCoreRole == MeshCoreRole.ROOM_SERVER }
            val sampleImg = com.example.model.PreparedFirmwareImage(
                bytes = ByteArray(2048) { 0xE9.toByte() },
                sha256Hex = "abcdef1234567890",
                flashOffset = 0x0000,
                fileName = "Station_G2_room_server-v1.18.0-81dbae5-merged.bin",
                isFullFactoryImage = true,
                isRealBinary = true
            )
            vault.savePreparedImageToVault(g2RoomSpec, "v1.18.0", sampleImg)
            val allEntries = vault.loadAllEntries()
            assertEquals(1, allEntries.size)

            val matchedEntry = vault.findMatchingEntry(
                entries = allEntries,
                board = stationG2,
                ecosystem = FirmwareEcosystem.MESHCORE,
                meshCoreRole = MeshCoreRole.ROOM_SERVER,
                fullErase = false,
                useMeshtasticMui = true,
                tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE
            )
            assertNotNull(matchedEntry)
            val loaded = vault.loadPreparedImageFromEntry(matchedEntry!!)
            assertNotNull(loaded)
            assertEquals("Station_G2_room_server-v1.18.0-81dbae5-merged.bin", loaded!!.fileName)
            assertEquals(2048, loaded.bytes.size)
            // Test Meshtastic vault preparation and saving
            val mtSpecs = specs.filter { it.ecosystem == FirmwareEcosystem.MESHTASTIC }
            assertEquals(5, mtSpecs.size) // 3 for T-Deck (MUI Factory, MUI Update, BaseUI) + 2 for Station G2 (Factory, Update)
            val mtRelease = repo.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()

            for (spec in mtSpecs) {
                val prepared = repo.prepareFirmwarePayloadForVault(
                    board = spec.board,
                    release = mtRelease,
                    meshCoreRole = spec.meshCoreRole,
                    fullErase = spec.fullErase,
                    useMeshtasticMui = spec.useMeshtasticMui,
                    tDeckMeshCoreFlavor = spec.tDeckMeshCoreFlavor,
                    onProgress = { _, _ -> }
                )
                assertNotNull(prepared)
                assertTrue(prepared.bytes.isNotEmpty())
                vault.savePreparedImageToVault(spec, mtRelease.tagName, prepared)
            }

            val updatedEntries = vault.loadAllEntries()
            assertEquals(6, updatedEntries.size) // 1 MeshCore + 5 Meshtastic

            // Verify Station G2 Meshtastic matching when fullErase = false (update mode)
            val g2MtMatched = vault.findMatchingEntry(
                entries = updatedEntries,
                board = stationG2,
                ecosystem = FirmwareEcosystem.MESHTASTIC,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = false,
                useMeshtasticMui = true,
                tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE
            )
            assertNotNull("Station G2 Meshtastic factory entry should match update request offline", g2MtMatched)
            assertEquals(FirmwareEcosystem.MESHTASTIC, g2MtMatched!!.ecosystem)

            // Verify LilyGO T-Deck Meshtastic MUI matching
            val tDeckMuiMatched = vault.findMatchingEntry(
                entries = updatedEntries,
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                ecosystem = FirmwareEcosystem.MESHTASTIC,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = false,
                useMeshtasticMui = true,
                tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
                targetVersionTag = mtRelease.tagName
            )
            assertNotNull("T-Deck Meshtastic MUI entry should match offline", tDeckMuiMatched)
            assertTrue("MUI entry should have variantKey with mui", tDeckMuiMatched!!.variantKey.contains("mui"))
            assertTrue("MUI entry should have t-deck-tft filename", tDeckMuiMatched.fileName.contains("t-deck-tft"))

            // Verify LilyGO T-Deck Meshtastic BaseUI matching (must NOT match MUI)
            val tDeckBaseUiMatched = vault.findMatchingEntry(
                entries = updatedEntries,
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                ecosystem = FirmwareEcosystem.MESHTASTIC,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = true,
                useMeshtasticMui = false,
                tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
                targetVersionTag = mtRelease.tagName
            )
            assertNotNull("T-Deck Meshtastic BaseUI entry should match offline", tDeckBaseUiMatched)
            assertTrue("BaseUI entry should have variantKey with baseui", tDeckBaseUiMatched!!.variantKey.contains("baseui"))
            assertTrue("BaseUI entry should have t-deck filename without tft", tDeckBaseUiMatched.fileName.contains("t-deck") && !tDeckBaseUiMatched.fileName.contains("tft"))
            assertTrue("MUI and BaseUI filenames must be different", tDeckMuiMatched.fileName != tDeckBaseUiMatched.fileName)

            // Verify LilyGO T-Deck on Alpha side (MUI vs BaseUI on Alpha release)
            val mtAlphaRelease = repo.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
            assertTrue("Fallback last must be alpha prerelease", mtAlphaRelease.isPrerelease)
            val alphaMuiName = repo.resolveExpectedFileName(
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                release = mtAlphaRelease,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = true,
                useMeshtasticMui = true
            )
            assertTrue("Alpha MUI name must contain t-deck-tft and alpha version", alphaMuiName.contains("t-deck-tft") && alphaMuiName.contains("2.8.1"))

            val alphaBaseUiName = repo.resolveExpectedFileName(
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                release = mtAlphaRelease,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = true,
                useMeshtasticMui = false
            )
            assertTrue("Alpha BaseUI name must contain t-deck- and NOT tft", alphaBaseUiName.contains("t-deck-") && !alphaBaseUiName.contains("tft") && alphaBaseUiName.contains("2.8.1"))
            assertTrue("Alpha MUI and Alpha BaseUI filenames must be different", alphaMuiName != alphaBaseUiName)

            // Verify saving and matching an Alpha T-Deck entry in the vault
            val alphaSpec = com.example.model.VaultTargetSpec(
                key = vault.buildVaultKey(BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" }, FirmwareEcosystem.MESHTASTIC, meshtasticTrack = com.example.model.MeshtasticTrack.ALPHA, useMeshtasticMui = false, fullErase = true),
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                ecosystem = FirmwareEcosystem.MESHTASTIC,
                variantKey = vault.buildVariantKey(BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" }, FirmwareEcosystem.MESHTASTIC, meshtasticTrack = com.example.model.MeshtasticTrack.ALPHA, useMeshtasticMui = false, fullErase = true),
                variantLabel = vault.buildVariantLabel(BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" }, FirmwareEcosystem.MESHTASTIC, meshtasticTrack = com.example.model.MeshtasticTrack.ALPHA, useMeshtasticMui = false, fullErase = true),
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                meshtasticTrack = com.example.model.MeshtasticTrack.ALPHA,
                useMeshtasticMui = false,
                tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
                fullErase = true
            )
            val sampleAlphaImg = com.example.model.PreparedFirmwareImage(
                bytes = ByteArray(1024) { 0x55.toByte() },
                sha256Hex = "1122334455667788",
                flashOffset = 0x0000,
                fileName = "firmware-t-deck-2.8.1.8e6a88d.factory.bin",
                isFullFactoryImage = true,
                isRealBinary = true
            )
            vault.savePreparedImageToVault(alphaSpec, mtAlphaRelease.tagName, sampleAlphaImg)
            val updatedEntriesWithAlpha = vault.loadAllEntries()

            val alphaBaseUiMatched = vault.findMatchingEntry(
                entries = updatedEntriesWithAlpha,
                board = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" },
                ecosystem = FirmwareEcosystem.MESHTASTIC,
                meshCoreRole = MeshCoreRole.COMPANION_BLE,
                fullErase = true,
                useMeshtasticMui = false,
                meshtasticTrack = com.example.model.MeshtasticTrack.ALPHA,
                targetVersionTag = mtAlphaRelease.tagName
            )
            assertNotNull("Alpha BaseUI entry must be found in vault", alphaBaseUiMatched)
            assertTrue(alphaBaseUiMatched!!.variantKey.contains("baseui"))
            assertTrue(alphaBaseUiMatched.variantKey.contains("alpha"))
            assertEquals("firmware-t-deck-2.8.1.8e6a88d.factory.bin", alphaBaseUiMatched.fileName)
        }
    }

    @Test
    fun `launch MainActivity without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `verify UsbTransportManager and BleTransportManager boot safely without security exception`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val logs = mutableListOf<String>()
        val usb = com.example.hardware.UsbTransportManager(
            context = app,
            onLog = { _, tag, msg -> logs.add("[$tag] $msg") },
            onDevicesChanged = {}
        )
        // Verify registerReceiver and scanning does not crash
        usb.registerReceiver()
        val devices = usb.scanUsbDevices()
        assertNotNull(devices)
        usb.unregisterReceiver()

        val ble = com.example.hardware.BleTransportManager(
            context = app,
            onLog = { _, tag, msg -> logs.add("[$tag] $msg") },
            onNodesUpdated = {}
        )
        // Verify Bluetooth checks do not throw
        val bleEnabled = ble.isBluetoothEnabled()
        assertTrue(bleEnabled || !bleEnabled)
        ble.stopBleScan()
    }

    @Test
    fun `verify MeshInstallerViewModel init boots without crash and populates initial state`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.MeshInstallerViewModel(app)
        val state = vm.uiState.value
        assertNotNull(state.selectedBoard)
        assertTrue(state.meshtasticReleases.isNotEmpty())
        assertTrue(state.meshCoreReleases.isNotEmpty())
        assertNotNull(state.selectedRelease)
        assertTrue(state.consoleLogs.any { it.message.contains("Mesh Installer initialized") })
    }
}


