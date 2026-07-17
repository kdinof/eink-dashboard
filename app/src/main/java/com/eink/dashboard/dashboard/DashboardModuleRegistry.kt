package com.eink.dashboard.dashboard

/**
 * The single place that knows which [DashboardModule]s exist. The shell reads
 * the ordered list to lay out blocks; the coordinator iterates it to refresh.
 *
 * Registration order is the canonical block order used by [DashboardLayoutSpec].
 * The registry is built once at app start (see `EinkDashApp`) and is immutable
 * afterwards — modules do not come and go at runtime; the user only hides/shows
 * them via settings, which is a view concern, not a registration concern.
 *
 * ### Demo safety
 * A module with `isDemo == true` is only accepted when [allowDemo] is true. The
 * app passes `BuildConfig.DEBUG` there, so sample data can never be registered
 * in a release build and be mistaken for the user's real calendar/tasks.
 */
class DashboardModuleRegistry private constructor(
    private val modules: List<DashboardModule>,
) {
    /** All registered modules in canonical (registration) order. */
    val all: List<DashboardModule> get() = modules

    fun byId(id: String): DashboardModule? = modules.firstOrNull { it.id == id }

    val ids: List<String> get() = modules.map { it.id }

    class Builder(private val allowDemo: Boolean) {
        private val modules = mutableListOf<DashboardModule>()

        /**
         * Register [module]. Throws if its id collides with an already-registered
         * one, or if it is a demo module while [allowDemo] is false (release).
         */
        fun register(module: DashboardModule): Builder {
            require(allowDemo || !module.isDemo) {
                "Refusing to register demo module '${module.id}' in a non-debug build"
            }
            require(modules.none { it.id == module.id }) {
                "Duplicate module id '${module.id}'"
            }
            modules += module
            return this
        }

        /** Registers each of [candidates], applying the same rules as [register]. */
        fun registerAll(candidates: Iterable<DashboardModule>): Builder {
            candidates.forEach(::register)
            return this
        }

        fun build(): DashboardModuleRegistry = DashboardModuleRegistry(modules.toList())
    }

    companion object {
        fun builder(allowDemo: Boolean): Builder = Builder(allowDemo)

        /** An empty registry — the valid state when no modules are registered yet. */
        fun empty(): DashboardModuleRegistry = DashboardModuleRegistry(emptyList())
    }
}
