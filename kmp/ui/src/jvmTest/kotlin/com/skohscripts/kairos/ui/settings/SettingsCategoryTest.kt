package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.GeneralError
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/spec/reglages.md § Critères de succès : chaque champ et chaque règle entre champs a sa catégorie. */
class SettingsCategoryTest {
    @Test
    fun everyFieldOfTheFormBelongsToACategory() {
        // `of` lève une erreur pour une clé sans catégorie : un champ ajouté sans elle casse ici, pas en silence à l'écran.
        val categories = SettingsForm.values(Settings()).keys.map { SettingsCategory.of(it) }.toSet()
        assertEquals(
            setOf(SettingsCategory.GENERAL, SettingsCategory.DAY, SettingsCategory.PLANNING, SettingsCategory.TRACKING, SettingsCategory.TEAM),
            categories,
        )
    }

    @Test
    fun fieldsAreFiledWhereTheScreenShowsThem() {
        mapOf(
            "language" to SettingsCategory.GENERAL,
            "themeColor" to SettingsCategory.GENERAL,
            "updateCheckEnabled" to SettingsCategory.GENERAL,
            "workdayStartHour" to SettingsCategory.DAY,
            "schedule.d0.mode" to SettingsCategory.DAY,
            "schedule.c0.name" to SettingsCategory.DAY,
            "extraHolidays" to SettingsCategory.DAY,
            // Sous `schedule.` mais rangées avec ce qui ordonne les tâches.
            "schedule.bestHours.enabled" to SettingsCategory.PLANNING,
            "taskTypes" to SettingsCategory.PLANNING,
            "learning.history" to SettingsCategory.PLANNING,
            "priorityValueBase" to SettingsCategory.PLANNING,
            "cognitiveDipPenalty" to SettingsCategory.PLANNING,
            "pomodoroFocusMinutes" to SettingsCategory.TRACKING,
            "statsWindowWeeks" to SettingsCategory.TRACKING,
            "team.wipLimit" to SettingsCategory.TEAM,
        ).forEach { (key, category) -> assertEquals(category, SettingsCategory.of(key), key) }
    }

    @Test
    fun eachRuleBetweenFieldsPointsToTheCategoryOfItsFields() {
        assertEquals(SettingsCategory.DAY, SettingsCategory.of(GeneralError.WORKDAY_ORDER))
        assertEquals(SettingsCategory.PLANNING, SettingsCategory.of(GeneralError.DIP_ORDER))
    }

    @Test
    fun anUnknownKeyIsRefused() {
        assertFailsWith<IllegalStateException> { SettingsCategory.of("nothing.here") }
    }
}
