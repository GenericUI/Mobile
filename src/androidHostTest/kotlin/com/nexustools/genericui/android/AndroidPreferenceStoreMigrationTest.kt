package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.component.system.PreferenceSerializable
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.util.BinaryJson
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPreferenceStoreMigrationTest {

    private data class ProfileSettings(
        val profileName: String,
        val fontSize: Int,
        val refreshMs: Long,
        val zoomRatio: Double,
        val darkMode: Boolean,
        val pinnedTabs: List<String>
    )

    private data class SerializableBundle(
        val label: String,
        val order: List<String>
    ) : PreferenceSerializable {
        override fun toPreferenceMap(): Map<String, Any?> = linkedMapOf(
            "label" to label,
            "order" to order
        )

        override fun fromPreferenceElements(fields: Map<String, PreferenceElement>): Any = SerializableBundle(
            label = fields["label"]?.asString() ?: label,
            order = fields["order"]?.arrayElements?.mapNotNull { it.asString() } ?: order
        )
    }

    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        AndroidPreferenceStoreImpl.applicationContext = null
        AndroidViewHandle.activeContext = null
        context = RuntimeEnvironment.getApplication()
    }

    @AfterTest
    fun tearDown() {
        AndroidPreferenceStoreImpl.applicationContext = null
        AndroidViewHandle.activeContext = null
    }

    @Test
    fun writesDirectlyToAndroidSharedPreferencesWithoutCreatingBsonFiles() {
        val appName = "DirectPrefsApp_${System.nanoTime()}"
        val cleanName = AndroidPreferenceStoreImpl.sanitizePrefsName(appName)
        val impl = AndroidPreferenceStoreImpl(appName = appName, context = context)

        assertEquals("SharedPreferences($cleanName)", impl.storageLocationDescription)
        assertTrue(impl.usesNativeChangeListener)

        impl.writeElement("enabled", PreferenceElement.ofBoolean(true))
        impl.writeElement("retries", PreferenceElement.ofInt(5))
        impl.writeElement("timestamp", PreferenceElement.ofLong(1_700_000_000_123L))
        impl.writeElement("scale", PreferenceElement.ofFloat(1.25f))
        impl.writeElement("pi", PreferenceElement.ofDouble(3.141592653589793))
        impl.writeElement("host", PreferenceElement.ofString("99.250.105.120"))
        assertTrue(impl.flush())

        val nativePrefs = context.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        assertEquals(true, nativePrefs.getBoolean("enabled", false))
        assertEquals(5, nativePrefs.getInt("retries", -1))
        assertEquals(1_700_000_000_123L, nativePrefs.getLong("timestamp", -1L))
        assertEquals(1.25f, nativePrefs.getFloat("scale", -1f))
        assertEquals("3.141592653589793", nativePrefs.getString("pi", null))
        assertEquals("99.250.105.120", nativePrefs.getString("host", null))

        val legacyDir = File(context.filesDir, "genericui-prefs/$cleanName")
        assertFalse(legacyDir.exists(), "Must not create legacy .bson directory on Android")

        impl.dispose()
    }

    @Test
    fun roundTripsPrimitivesAmbiguousStringsSetsOrderedListsAndStructuredObjects() {
        val appName = "RoundTripPrefsApp_${System.nanoTime()}"
        val cleanName = AndroidPreferenceStoreImpl.sanitizePrefsName(appName)
        val factory = AndroidNativeFactory(context)
        val store = PreferenceStore(appName = appName, factory = factory)

        val profile = ProfileSettings(
            profileName = "studio-main",
            fontSize = 15,
            refreshMs = 60_000L,
            zoomRatio = 1.875,
            darkMode = true,
            pinnedTabs = listOf("timeline", "assets", "inspector", "timeline")
        )
        val customSerializable = SerializableBundle(
            label = "workspace",
            order = listOf("third", "first", "second")
        )
        val structuredMap = linkedMapOf<String, Any?>(
            "endpoint" to "99.250.105.120",
            "port" to 8443,
            "tls" to true,
            "ratio" to 2.5,
            "routes" to listOf("/health", "/metrics", "/api/v1")
        )
        val orderedList = listOf("zeta", "alpha", "mu", "alpha")
        val stringSet = linkedSetOf("alpha", "beta", "gamma")

        store.edit {
            putBoolean("bool_true", true)
            putBoolean("bool_false", false)
            put("byte_val", 7.toByte())
            put("short_val", 312.toShort())
            putInt("int_val", -98765)
            putLong("long_val", 9_000_000_001L)
            putFloat("float_val", 2.375f)
            putDouble("double_val", 10.0)
            putDouble("double_precise", 3.141592653589793)
            putString("str_decimal", "10.0")
            putString("str_json_like", "{\"not\":\"json\"}")
            putString("str_ip", "99.250.105.120")
            putString("str_empty", "")
            putStringSet("string_set", stringSet)
            putStringList("ordered_list", orderedList)
            put("map_val", structuredMap)
            put("profile_obj", profile)
            put("serializable_obj", customSerializable)
        }

        // Seed pre-existing raw SharedPreferences keys directly without GenericUI metadata
        val nativePrefs = context.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        assertTrue(
            nativePrefs.edit()
                .putBoolean("raw_bool", true)
                .putInt("raw_int", 42)
                .putLong("raw_long", 8_000_000_007L)
                .putFloat("raw_float", 4.5f)
                .putStringSet("raw_set", setOf("red", "blue"))
                .putString("raw_plain_str", "99.250.105.120")
                .putString("raw_decimal_str", "10.0")
                .putString("raw_json_obj", "{\"k\":123,\"ok\":true}")
                .putString("raw_json_arr", "[\"first\",\"second\"]")
                .commit()
        )
        store.dispose()

        // Reopen with a brand-new instance to ensure full SharedPreferences persistence
        val reopened = PreferenceStore(appName = appName, factory = factory)
        assertEquals(true, reopened.getBoolean("bool_true"))
        assertEquals(false, reopened.getBoolean("bool_false", defaultValue = true))
        assertEquals(7.toByte(), reopened.get<Byte>("byte_val"))
        assertEquals(312.toShort(), reopened.get<Short>("short_val"))
        assertEquals(-98765, reopened.getInt("int_val"))
        assertEquals(9_000_000_001L, reopened.getLong("long_val"))
        assertEquals(2.375f, reopened.getFloat("float_val"))

        val doubleElem = reopened.getElement("double_val")
        assertNotNull(doubleElem)
        assertEquals(PreferenceElementKind.NUMBER, doubleElem.kind)
        assertEquals(PreferenceElement.NUMBER_TAG_DOUBLE, doubleElem.numberTypeTag)
        assertEquals(10.0, reopened.getDouble("double_val"))
        assertEquals(3.141592653589793, reopened.getDouble("double_precise"))

        val decimalStrElem = reopened.getElement("str_decimal")
        assertNotNull(decimalStrElem)
        assertEquals(PreferenceElementKind.STRING, decimalStrElem.kind)
        assertEquals("10.0", reopened.getString("str_decimal"))
        assertEquals("10.0", reopened.get<Any>("str_decimal"))

        val jsonLikeStrElem = reopened.getElement("str_json_like")
        assertNotNull(jsonLikeStrElem)
        assertEquals(PreferenceElementKind.STRING, jsonLikeStrElem.kind)
        assertEquals("{\"not\":\"json\"}", reopened.getString("str_json_like"))
        assertEquals("{\"not\":\"json\"}", reopened.get<Any>("str_json_like"))

        assertEquals("99.250.105.120", reopened.getString("str_ip"))
        assertTrue(reopened.contains("str_empty"))
        assertEquals("", reopened.getString("str_empty"))

        assertEquals(stringSet, reopened.getStringSet("string_set"))
        assertEquals(orderedList, reopened.getStringList("ordered_list"))

        val loadedMap = reopened.get<Map<String, Any?>>("map_val")
        assertNotNull(loadedMap)
        assertEquals("99.250.105.120", loadedMap["endpoint"])
        assertEquals(8443, loadedMap["port"])
        assertEquals(true, loadedMap["tls"])
        assertEquals(2.5, loadedMap["ratio"])
        assertEquals(listOf("/health", "/metrics", "/api/v1"), loadedMap["routes"])

        val loadedProfile = reopened.get<ProfileSettings>("profile_obj")
        assertEquals(profile, loadedProfile)

        val loadedSerializable = reopened.getOrDefault(
            "serializable_obj",
            SerializableBundle(label = "fallback", order = emptyList())
        )
        assertEquals(customSerializable, loadedSerializable)

        // Verify raw SharedPreferences compatibility
        assertEquals(true, reopened.getBoolean("raw_bool"))
        assertEquals(42, reopened.getInt("raw_int"))
        assertEquals(8_000_000_007L, reopened.getLong("raw_long"))
        assertEquals(4.5f, reopened.getFloat("raw_float"))
        assertEquals(setOf("red", "blue"), reopened.getStringSet("raw_set"))
        assertEquals("99.250.105.120", reopened.getString("raw_plain_str"))
        assertEquals("10.0", reopened.getString("raw_decimal_str"))
        val rawParsedMap = reopened.get<Map<String, Any?>>("raw_json_obj")
        assertEquals(123, rawParsedMap?.get("k"))
        assertEquals(true, rawParsedMap?.get("ok"))
        assertEquals(listOf("first", "second"), reopened.getStringList("raw_json_arr"))

        reopened.dispose()
    }

    @Test
    fun buffersInMemoryBeforeContextAndFlushesToSharedPreferencesWhenContextAttaches() {
        val appName = "DelayPrefApp_${System.nanoTime()}"
        val cleanName = AndroidPreferenceStoreImpl.sanitizePrefsName(appName)

        // Seed existing SharedPreferences entry before store construction
        val nativePrefs = context.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        assertTrue(
            nativePrefs.edit()
                .putString("already_saved", "persisted-value")
                .putString("override_key", "stale-value")
                .commit()
        )

        // Construct store BEFORE applicationContext or activeContext is set
        val store = AndroidPreferenceStoreImpl(appName = appName)
        store.writeElement("buffered_str", PreferenceElement.ofString("queued-val"))
        store.writeElement("buffered_decimal_str", PreferenceElement.ofString("10.0"))
        store.writeElement("buffered_double", PreferenceElement.ofDouble(4.25))
        store.writeElement("override_key", PreferenceElement.ofString("fresh-buffered"))
        store.writeElement(
            "buffered_obj",
            PreferenceObjectCodec.toElement(mapOf("items" to listOf("one", "two")))
        )

        assertTrue(store.contains("buffered_str"))
        assertEquals("queued-val", store.readElement("buffered_str")?.asString())
        assertEquals(
            setOf("buffered_decimal_str", "buffered_double", "buffered_obj", "buffered_str", "override_key"),
            store.keys()
        )
        assertFalse(nativePrefs.contains("buffered_str"))

        // Attach applicationContext dynamically after writes were buffered
        AndroidPreferenceStoreImpl.applicationContext = context

        assertEquals("persisted-value", store.readElement("already_saved")?.asString())
        assertEquals("fresh-buffered", store.readElement("override_key")?.asString())
        assertEquals("queued-val", store.readElement("buffered_str")?.asString())
        assertEquals(PreferenceElementKind.STRING, store.readElement("buffered_decimal_str")?.kind)
        assertEquals("10.0", store.readElement("buffered_decimal_str")?.asString())
        assertEquals(4.25, store.readElement("buffered_double")?.asDouble())
        assertTrue(store.flush())

        // Verify a reopened instance sees both preserved SharedPreferences keys and flushed buffered keys
        val reopened = AndroidPreferenceStoreImpl(appName = appName, context = context)
        assertEquals("persisted-value", reopened.readElement("already_saved")?.asString())
        assertEquals("fresh-buffered", reopened.readElement("override_key")?.asString())
        assertEquals("queued-val", reopened.readElement("buffered_str")?.asString())
        assertEquals("10.0", reopened.readElement("buffered_decimal_str")?.asString())
        assertEquals(4.25, reopened.readElement("buffered_double")?.asDouble())
        assertEquals(
            listOf("one", "two"),
            reopened.readElement("buffered_obj")?.get("items")?.arrayElements?.mapNotNull { it.asString() }
        )

        store.dispose()
        reopened.dispose()
    }

    @Test
    fun migratesLegacyBsonAndBackupFilesOnceIntoSharedPreferencesAndDeletesLegacyFiles() {
        val appName = "LegacyBsonApp_${System.nanoTime()}"
        val cleanName = AndroidPreferenceStoreImpl.sanitizePrefsName(appName)

        val sharedPrefs = context.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        assertTrue(sharedPrefs.edit().putString("alreadyPresent", "modern-shared-val").commit())

        val legacyDir = File(context.filesDir, "genericui-prefs/$cleanName").apply { mkdirs() }
        File(legacyDir, "migrated_str.bson").writeBytes(
            BinaryJson.encodeElement(PreferenceElement.ofString("10.0"))
        )
        File(legacyDir, "migrated_double.bson").writeBytes(
            BinaryJson.encodeElement(PreferenceElement.ofDouble(12.75))
        )
        File(legacyDir, "migrated_list.bson").writeBytes(
            BinaryJson.encodeElement(
                PreferenceElement.ofArray(
                    listOf(PreferenceElement.ofString("second"), PreferenceElement.ofString("first"))
                )
            )
        )
        // Corrupt primary .bson should fall back to valid .bson.old
        File(legacyDir, "recovered_backup.bson").writeBytes(byteArrayOf(0x09, 0x7F, 0x00))
        File(legacyDir, "recovered_backup.bson.old").writeBytes(
            BinaryJson.encodeElement(PreferenceElement.ofString("recovered-old-val"))
        )
        File(legacyDir, "backup_only.bson.old").writeBytes(
            BinaryJson.encodeElement(PreferenceElement.ofLong(9_000_000_001L))
        )
        // Existing SharedPreferences key must NOT be overwritten by stale .bson
        File(legacyDir, "alreadyPresent.bson").writeBytes(
            BinaryJson.encodeElement(PreferenceElement.ofString("stale-bson-val"))
        )
        File(legacyDir, ".sharedprefs-migrated").writeText("1")

        val store = AndroidPreferenceStoreImpl(appName = appName, context = context)

        assertEquals("modern-shared-val", store.readElement("alreadyPresent")?.asString())
        assertEquals(PreferenceElementKind.STRING, store.readElement("migrated_str")?.kind)
        assertEquals("10.0", store.readElement("migrated_str")?.asString())
        assertEquals(PreferenceElementKind.NUMBER, store.readElement("migrated_double")?.kind)
        assertEquals(12.75, store.readElement("migrated_double")?.asDouble())
        assertEquals(
            listOf("second", "first"),
            store.readElement("migrated_list")?.arrayElements?.mapNotNull { it.asString() }
        )
        assertEquals("recovered-old-val", store.readElement("recovered_backup")?.asString())
        assertEquals(9_000_000_001L, store.readElement("backup_only")?.asLong())

        assertFalse(legacyDir.exists(), "Legacy .bson directory should be deleted after migration")

        // Delete a migrated key and verify a newly opened store keeps it deleted
        store.remove("migrated_str")
        assertFalse(store.contains("migrated_str"))
        assertNull(store.readElement("migrated_str"))

        val secondStore = AndroidPreferenceStoreImpl(appName = appName, context = context)
        assertNull(secondStore.readElement("migrated_str"))
        assertEquals("recovered-old-val", secondStore.readElement("recovered_backup")?.asString())

        store.dispose()
        secondStore.dispose()
    }

    @Test
    fun removeClearKeysContainsFlushAndChangeListenersWorkCleanly() {
        val appName = "ListenerPrefsApp_${System.nanoTime()}"
        val cleanName = AndroidPreferenceStoreImpl.sanitizePrefsName(appName)
        val store = AndroidPreferenceStoreImpl(appName = appName, context = context)

        val events = mutableListOf<Triple<String, String?, Any?>>()
        val listener = PreferenceChangeListener { storeName, key, newValue ->
            events.add(Triple(storeName, key, newValue))
        }
        store.addChangeListener(listener)

        store.writeElement("alpha", PreferenceElement.ofString("one"))
        store.writeElement("beta", PreferenceElement.ofInt(2))
        store.writeElement("gamma", PreferenceElement.ofString("{\"not\":\"json\"}"))

        assertEquals(3, events.size)
        assertEquals(Triple(appName, "alpha", "one"), events[0])
        assertEquals(Triple(appName, "beta", 2), events[1])
        assertEquals(Triple(appName, "gamma", "{\"not\":\"json\"}"), events[2])
        assertEquals(setOf("alpha", "beta", "gamma"), store.keys())
        assertTrue(store.contains("beta"))

        // External SharedPreferences write must notify via OnSharedPreferenceChangeListener
        val nativePrefs = context.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        assertTrue(nativePrefs.edit().putInt("external_key", 99).commit())
        assertEquals(4, events.size)
        assertEquals(Triple(appName, "external_key", 99), events[3])
        assertTrue(store.contains("external_key"))

        // Writing PreferenceElement.Null removes key and notifies null once
        store.writeElement("alpha", PreferenceElement.Null)
        assertFalse(store.contains("alpha"))
        assertEquals(5, events.size)
        assertEquals(Triple(appName, "alpha", null), events[4])

        // Removing key notifies once
        store.remove("beta")
        assertFalse(store.contains("beta"))
        assertEquals(6, events.size)
        assertEquals(Triple(appName, "beta", null), events[5])

        assertTrue(store.flush())

        // Clearing removes all entries in both prefs and metadata prefs and notifies (appName, null, null) once
        store.clear()
        assertTrue(store.keys().isEmpty())
        assertTrue(nativePrefs.all.isEmpty())
        val metaPrefs = context.getSharedPreferences("${cleanName}_genericui_kinds", Context.MODE_PRIVATE)
        assertTrue(metaPrefs.all.isEmpty())
        assertEquals(7, events.size)
        assertEquals(Triple(appName, null, null), events[6])

        store.removeChangeListener(listener)
        store.dispose()
        assertTrue(store.isDisposed)
        assertFalse(store.flush())

        // Disposed store must no longer dispatch listener events on external changes
        assertTrue(nativePrefs.edit().putString("after_dispose", "ignored").commit())
        assertEquals(7, events.size)
    }
}
