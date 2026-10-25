package io.github.ranxian.muguang.ui

internal object UserCardLayoutPolicy {
    data class CardPages(
        val profile: List<UserCardType>,
        val settings: List<UserCardType>
    )

    /**
     * 不允许挪到设置页、也不允许隐藏的卡片。
     *
     * 账户信息就是「这个账号是谁」——姓名、学校、学号只在这里出现。
     * 一旦被挪走或藏起来，用户看到的只是「我的页面空了」，第一反应是账号丢了，
     * 而不是「我之前点过隐藏」。充值同理：入口只该有一个位置，藏起来等于功能消失。
     * 这两张卡片仍然可以上下排序。
     */
    val pinned = setOf(UserCardType.ACCOUNT, UserCardType.RECHARGE)

    /**
     * 当前布局版本。改动 [profileDefaults] 的顺序时 +1，
     * 老存档会按 [migrateProfileOrder] 做一次性迁移。
     */
    const val LAYOUT_VERSION = 2

    val profileDefaults = listOf(
        UserCardType.ACCOUNT,
        UserCardType.RECHARGE,
        UserCardType.BOUND_ROOM,
        UserCardType.USE_CODE
    )

    val settingsDefaults = listOf(
        UserCardType.NOTIFY,
        UserCardType.TREND,
        UserCardType.BACKGROUND,
        UserCardType.LOG,
        UserCardType.ABOUT,
        UserCardType.UPDATE
    )

    fun restore(defaults: List<UserCardType>, saved: String): List<UserCardType> {
        val allowed = defaults.toSet()
        val restored = saved.split(',')
            .mapNotNull { value ->
                UserCardType.entries.firstOrNull { it.name == value.trim() }
            }
            .filter { it in allowed }
        return (restored + defaults).distinct()
    }

    fun restorePages(
        profileSaved: String,
        settingsSaved: String,
        legacySaved: String = ""
    ): CardPages {
        val known = (profileDefaults + settingsDefaults).toSet()
        val useLegacy = profileSaved.isBlank() && settingsSaved.isBlank() && legacySaved.isNotBlank()
        val legacy = if (useLegacy) parse(legacySaved) else emptyList()
        val profileSource = if (useLegacy) legacy.filter { it in profileDefaults } else parse(profileSaved)
        val settingsSource = if (useLegacy) legacy.filter { it in settingsDefaults } else parse(settingsSaved)
        val profile = profileSource.filter { it in known }.distinct().toMutableList()
        val settings = settingsSource
            .filter { it in known && it !in profile }
            .distinct()
            .toMutableList()
        val assigned = (profile + settings).toMutableSet()

        profileDefaults.filterTo(profile) { assigned.add(it) }
        settingsDefaults.filterTo(settings) { assigned.add(it) }

        // 老版本允许把「账户信息 / 充值」挪到设置页，存档里可能真就躺在那儿，
        // 用户升级后看到的是「我的」少了两张卡。这里强制搬回来，
        // 位置按默认顺序插（ACCOUNT 在 0、RECHARGE 在 1），不动用户已有的相对顺序。
        pinned.sortedBy { profileDefaults.indexOf(it) }.forEach { type ->
            settings.remove(type)
            if (type !in profile) {
                profile.add(profileDefaults.indexOf(type).coerceIn(0, profile.size), type)
            }
        }
        return CardPages(profile, settings)
    }

    /**
     * 布局版本迁移：把「我的」页恢复成当前默认顺序。
     *
     * 只在 [version] 落后于 [LAYOUT_VERSION] 时执行一次。明确不做「只迁没动过的存档」——
     * 判断标准太脆（老版本会往存档里补卡片，存档本来就和新默认不一样），
     * 宁可让用户重排一次，也不能让「使用码应该在最下面」这种约定失效。
     * 从设置页搬进「我的」的卡片保留，跟在默认顺序后面。
     */
    fun migrateProfileOrder(
        version: Int,
        profile: List<UserCardType>
    ): List<UserCardType> {
        if (version >= LAYOUT_VERSION) return profile
        val extras = profile.filter { it !in profileDefaults }
        return profileDefaults + extras
    }

    /** 钉住的卡片（见 [pinned]）不参与搬家：原样返回 */
    fun moveToSettings(
        type: UserCardType,
        profile: List<UserCardType>,
        settings: List<UserCardType>
    ): CardPages = if (type in pinned) {
        CardPages(profile, settings)
    } else {
        CardPages(
            profile = profile.filterNot { it == type },
            settings = settings.filterNot { it == type } + type
        )
    }

    fun moveToProfile(
        type: UserCardType,
        profile: List<UserCardType>,
        settings: List<UserCardType>
    ): CardPages = CardPages(
        profile = profile.filterNot { it == type } + type,
        settings = settings.filterNot { it == type }
    )

    private fun parse(saved: String): List<UserCardType> = saved.split(',')
        .mapNotNull { value -> UserCardType.entries.firstOrNull { it.name == value.trim() } }
}
