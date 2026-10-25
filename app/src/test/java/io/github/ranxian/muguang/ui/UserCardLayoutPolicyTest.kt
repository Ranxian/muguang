package io.github.ranxian.muguang.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UserCardLayoutPolicyTest {
    @Test
    fun `profile page has four default cards`() {
        assertEquals(
            listOf(
                UserCardType.ACCOUNT,
                UserCardType.RECHARGE,
                UserCardType.USE_CODE,
                UserCardType.BOUND_ROOM
            ),
            UserCardLayoutPolicy.profileDefaults
        )
    }

    @Test
    fun `settings page uses requested default order`() {
        assertEquals(
            listOf(
                UserCardType.NOTIFY,
                UserCardType.TREND,
                UserCardType.BACKGROUND,
                UserCardType.LOG,
                UserCardType.ABOUT,
                UserCardType.UPDATE
            ),
            UserCardLayoutPolicy.settingsDefaults
        )
    }

    @Test
    fun `saved order is normalized within its own page`() {
        assertEquals(
            listOf(
                UserCardType.BOUND_ROOM,
                UserCardType.ACCOUNT,
                UserCardType.RECHARGE,
                UserCardType.USE_CODE
            ),
            UserCardLayoutPolicy.restore(
                UserCardLayoutPolicy.profileDefaults,
                "BOUND_ROOM,ACCOUNT"
            )
        )
        assertEquals(
            UserCardLayoutPolicy.settingsDefaults,
            UserCardLayoutPolicy.restore(UserCardLayoutPolicy.settingsDefaults, "ACCOUNT,USE_CODE")
        )
    }

    @Test
    fun `moving a profile card appends it to settings without duplication`() {
        val result = UserCardLayoutPolicy.moveToSettings(
            UserCardType.USE_CODE,
            UserCardLayoutPolicy.profileDefaults,
            UserCardLayoutPolicy.settingsDefaults
        )

        assertEquals(
            listOf(UserCardType.ACCOUNT, UserCardType.RECHARGE, UserCardType.BOUND_ROOM),
            result.profile
        )
        assertEquals(UserCardLayoutPolicy.settingsDefaults + UserCardType.USE_CODE, result.settings)
    }

    @Test
    fun `pinned cards cannot be moved to the settings page`() {
        UserCardLayoutPolicy.pinned.forEach { type ->
            val result = UserCardLayoutPolicy.moveToSettings(
                type,
                UserCardLayoutPolicy.profileDefaults,
                UserCardLayoutPolicy.settingsDefaults
            )
            assertEquals(UserCardLayoutPolicy.profileDefaults, result.profile)
            assertEquals(UserCardLayoutPolicy.settingsDefaults, result.settings)
        }
    }

    @Test
    fun `moving a settings card appends it to profile without duplication`() {
        val result = UserCardLayoutPolicy.moveToProfile(
            UserCardType.NOTIFY,
            UserCardLayoutPolicy.profileDefaults,
            UserCardLayoutPolicy.settingsDefaults
        )

        assertEquals(UserCardLayoutPolicy.profileDefaults + UserCardType.NOTIFY, result.profile)
        assertEquals(UserCardLayoutPolicy.settingsDefaults - UserCardType.NOTIFY, result.settings)
    }

    @Test
    fun `restoring pages keeps cards on their selected page`() {
        val result = UserCardLayoutPolicy.restorePages(
            profileSaved = "USE_CODE,BOUND_ROOM",
            settingsSaved = "NOTIFY,TREND,BACKGROUND,LOG,ABOUT,UPDATE"
        )

        assertEquals(
            listOf(
                UserCardType.USE_CODE,
                UserCardType.BOUND_ROOM,
                UserCardType.ACCOUNT,
                UserCardType.RECHARGE
            ),
            result.profile
        )
        assertEquals(UserCardLayoutPolicy.settingsDefaults, result.settings)
    }

    @Test
    fun `account card saved on the settings page is pulled back to the profile page`() {
        val result = UserCardLayoutPolicy.restorePages(
            profileSaved = "USE_CODE,BOUND_ROOM,RECHARGE",
            settingsSaved = "ACCOUNT,NOTIFY,TREND,BACKGROUND,LOG,ABOUT,UPDATE"
        )

        assertEquals(
            listOf(
                UserCardType.ACCOUNT,
                UserCardType.USE_CODE,
                UserCardType.BOUND_ROOM,
                UserCardType.RECHARGE
            ),
            result.profile
        )
        assertEquals(UserCardLayoutPolicy.settingsDefaults, result.settings)
    }

    @Test
    fun `legacy single page order is partitioned while preserving relative order`() {
        val result = UserCardLayoutPolicy.restorePages(
            profileSaved = "",
            settingsSaved = "",
            legacySaved = "LOG,BOUND_ROOM,ACCOUNT,NOTIFY,USE_CODE,BACKGROUND,UPDATE,ABOUT"
        )

        assertEquals(
            listOf(
                UserCardType.BOUND_ROOM,
                UserCardType.ACCOUNT,
                UserCardType.USE_CODE,
                UserCardType.RECHARGE
            ),
            result.profile
        )
        assertEquals(
            listOf(UserCardType.LOG, UserCardType.NOTIFY, UserCardType.BACKGROUND, UserCardType.UPDATE, UserCardType.ABOUT, UserCardType.TREND),
            result.settings
        )
    }
}
