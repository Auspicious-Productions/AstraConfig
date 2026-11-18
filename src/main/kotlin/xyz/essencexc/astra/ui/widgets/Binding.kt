package xyz.essencexc.astra.ui.widgets

/**
 * Where a widget reads and writes its value. For config options this goes through the option (so
 * listeners fire and the config saves); for anything else wrap a getter and setter with [of].
 */
interface Binding<T> {
    fun get(): T
    fun set(value: T)

    companion object {
        @JvmStatic
        fun <T> of(get: () -> T, set: (T) -> Unit): Binding<T> = object : Binding<T> {
            override fun get(): T = get()
            override fun set(value: T) = set(value)
        }

        /** A binding that just holds a value. Handy for previews and tests. */
        @JvmStatic
        fun <T> hold(initial: T): Binding<T> = object : Binding<T> {
            private var v = initial
            override fun get(): T = v
            override fun set(value: T) {
                v = value
            }
        }
    }
}
