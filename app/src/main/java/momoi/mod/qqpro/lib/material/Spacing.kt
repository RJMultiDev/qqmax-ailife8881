package momoi.mod.qqpro.lib.material

import momoi.mod.qqpro.lib.dp

/**
 * Phone-class 4dp spacing grid (Material 3 spacing tokens).
 *
 * Use [xs] / [sm] / [md] / [lg] / [xl] for all paddings, margins, and gaps inside the materialized UI
 * so every screen shares a single rhythm. These are dp values (already resolved against the device
 * density), so they drop into any [setPadding] / [setMargins] / layout-params call directly.
 *
 *     container.padding(left = Spacing.lg, top = Spacing.md, right = Spacing.lg, bottom = Spacing.md)
 *
 * Prefer these over hand-picked [Int.dp] values when the choice is just "how much space to put
 * between two things" — that way tweaking the rhythm only touches one file.
 */
object Spacing {
    /** 4dp — tight gaps inside a dense row (icon to label, badge to count). */
    val xs get() = 4.dp
    /** 8dp — secondary gaps (chip padding, list-item inner spacing). */
    val sm get() = 8.dp
    /** 16dp — default screen / list-item / card padding. The Material 3 "standard" gutter. */
    val md get() = 16.dp
    /** 24dp — section spacing, large card padding. */
    val lg get() = 24.dp
    /** 32dp — page-level vertical breathing room (e.g. between a hero block and a list). */
    val xl get() = 32.dp
}

/**
 * Phone-class standard component dimensions (mirror of [M3] size constants — exposed here so callers
 * that don't import M3 directly can still reach them). All values are dp (already density-resolved).
 */
object PhoneDimens {
    /** Material 3 minimum touch target size. Anything tappable should be at least this big. */
    val touchTargetMin get() = 48.dp
    /** Top app-bar height (M3 spec for phones). */
    val appBarHeight get() = 56.dp
    /** Bottom navigation height (M3 spec for phones). */
    val bottomNavHeight get() = 72.dp
    /** Round FAB diameter. */
    val fabSize get() = 56.dp
    /** Standard list-item minimum height (one line). */
    val listItemMinHeight get() = 56.dp
    /** Two-line list item height. */
    val listItemTwoLine get() = 72.dp
    /** Three-line list item height. */
    val listItemThreeLine get() = 88.dp
    /** Card default corner radius (M3 "large" shape). */
    val cardCornerRadius get() = M3.radiusLg
    /** FAB / app-bar elevation values. */
    val fabElevationRest get() = 6f
    val fabElevationPressed get() = 12f
    val appBarElevation get() = 0f
    val bottomNavElevation get() = 3f
    val cardElevation get() = 1f
}