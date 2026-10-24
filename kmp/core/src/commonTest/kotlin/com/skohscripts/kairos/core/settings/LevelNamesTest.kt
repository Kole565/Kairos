package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.model.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Noms des niveaux (docs/spec/planning-personnalise.md § Lot D, issue #93). */
class LevelNamesTest {
    private fun save(changes: Map<String, String>, base: Settings = Settings()): SettingsValidation =
        SettingsForm.validate(SettingsForm.values(base) + changes, base)

    @Test
    fun untouchedFormKeepsLevelNamesNull() {
        val values = SettingsForm.values(Settings())
        assertEquals("", values.getValue("levelNames.p0"))
        assertEquals("", values.getValue("levelNames.pts21"))
        assertNull(save(emptyMap()).settings!!.levelNames)
    }

    @Test
    fun namesAreTrimmedAndEmptyMeansDefault() {
        val saved = save(mapOf("levelNames.p0" to "  Urgent ", "levelNames.pts3" to "M", "levelNames.p1" to "   ")).settings!!
        assertEquals(LevelNames(priority = mapOf(0 to "Urgent"), points = mapOf(3 to "M")), saved.levelNames)
        assertEquals("Urgent", SettingsForm.values(saved).getValue("levelNames.p0"))
        // Tout vider : le sous-objet redevient nul.
        val cleared = save(mapOf("levelNames.p0" to "", "levelNames.pts3" to ""), saved).settings!!
        assertNull(cleared.levelNames)
    }

    @Test
    fun nameLongerThanSixteenIsRefused() {
        val ok = save(mapOf("levelNames.p2" to "a".repeat(LevelNames.MAX_LENGTH)))
        assertEquals("a".repeat(16), ok.settings!!.levelNames!!.priority[2])
        val tooLong = save(mapOf("levelNames.p2" to "a".repeat(LevelNames.MAX_LENGTH + 1)))
        assertNull(tooLong.settings)
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "16"), tooLong.fieldErrors["levelNames.p2"])
    }

    @Test
    fun duplicateNameInOneScaleIsRefusedOnTheSecond() {
        val result = save(mapOf("levelNames.pts2" to "Petit", "levelNames.pts5" to "petit"))
        assertNull(result.settings)
        assertEquals(FieldError(FieldErrorKind.DUPLICATE, "petit"), result.fieldErrors["levelNames.pts5"])
        assertFalse("levelNames.pts2" in result.fieldErrors)
        // Une priorité et un palier peuvent porter le même nom : ce sont deux échelles.
        assertTrue(save(mapOf("levelNames.p0" to "Gros", "levelNames.pts13" to "Gros")).settings != null)
    }

    @Test
    fun restoreDefaultsClearsNames() {
        val saved = save(mapOf("levelNames.p0" to "Urgent")).settings!!
        val restored = SettingsForm.restoreDefaults(SettingsForm.values(saved))
        assertEquals("", restored.getValue("levelNames.p0"))
        assertNull(SettingsForm.validate(restored, saved).settings!!.levelNames)
    }

    @Test
    fun emptyScaleIsNotWritten() {
        // Même configuration que l'export (`ExportCodec`) : un champ nul n'est pas écrit.
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val text = json.encodeToString(LevelNames(priority = mapOf(0 to "Urgent")))
        assertEquals("""{"priority":{"0":"Urgent"}}""", text)
        assertEquals(LevelNames(priority = mapOf(0 to "Urgent")), json.decodeFromString<LevelNames>(text))
        assertFalse("levelNames" in json.encodeToString(Settings()))
    }
}
