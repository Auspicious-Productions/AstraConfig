@file:JvmName("Functions")

package xyz.essencexc.astra.api.function

/** A condition re-evaluated by the menu, e.g. for `hideIf`. Java lambdas implement it directly. */
fun interface Condition {
    fun test(): Boolean
}

/** Called with the new value after an option changed through the menu or `Module.set`. */
fun interface OptionListener<T> {
    fun onChange(value: T)
}

/** A keybind action. */
fun interface KeyAction {
    fun run()
}

/** Schema migration for a mod's whole config object, see `AstraBuilder.migrate`. */
fun interface Migration {
    /** [json] is the raw saved object; mutate it in place. [from] is the saved schema version. */
    fun migrate(from: Int, json: com.google.gson.JsonObject)
}
