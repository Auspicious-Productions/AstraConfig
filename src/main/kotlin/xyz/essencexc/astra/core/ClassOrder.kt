package xyz.essencexc.astra.core

import java.io.DataInputStream
import java.io.IOException

/**
 * Orders classes by where they appear in their source file. Uses the smallest line number found in any
 * method's LineNumberTable (static initialisers and constructors included). The JVM's
 * `getDeclaredClasses()` order is unspecified, so nested `@Section` classes are sorted with this.
 */
internal object ClassOrder {
    fun sort(classes: List<Class<*>>): List<Class<*>> {
        val lines = classes.associateWith { firstLine(it) }
        return classes.sortedWith(compareBy<Class<*>> { lines[it] }.thenBy { it.name })
    }

    fun firstLine(cls: Class<*>): Int {
        val loader = cls.classLoader ?: return Int.MAX_VALUE
        return try {
            loader.getResourceAsStream(cls.name.replace('.', '/') + ".class")?.use { read(DataInputStream(it.buffered())) } ?: Int.MAX_VALUE
        } catch (_: IOException) {
            Int.MAX_VALUE
        }
    }

    private fun read(input: DataInputStream): Int {
        input.readInt() // magic
        input.readUnsignedShort() // minor
        input.readUnsignedShort() // major

        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        var i = 1
        while (i < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8[i] = input.readUTF()                      // Utf8
                3, 4 -> input.skipFully(4)                          // Integer, Float
                5, 6 -> { input.skipFully(8); i++ }                 // Long, Double take two slots
                7, 8, 16, 19, 20 -> input.skipFully(2)              // Class, String, MethodType, Module, Package
                9, 10, 11, 12, 17, 18 -> input.skipFully(4)         // refs, NameAndType, Dynamic, InvokeDynamic
                15 -> input.skipFully(3)                            // MethodHandle
                else -> throw IOException("Unknown constant pool tag $tag")
            }
            i++
        }

        input.skipFully(6)                                          // access, this, super
        input.skipFully(input.readUnsignedShort() * 2)              // interfaces

        repeat(input.readUnsignedShort()) { skipMember(input) }     // fields

        var min = Int.MAX_VALUE
        repeat(input.readUnsignedShort()) {                         // methods
            input.skipFully(6)
            repeat(input.readUnsignedShort()) {
                val name = utf8[input.readUnsignedShort()]
                val length = input.readInt()
                if (name == "Code") min = minOf(min, codeLine(input, utf8)) else input.skipFully(length)
            }
        }
        return min
    }

    private fun skipMember(input: DataInputStream) {
        input.skipFully(6)
        repeat(input.readUnsignedShort()) {
            input.skipFully(2)
            input.skipFully(input.readInt())
        }
    }

    private fun codeLine(input: DataInputStream, utf8: Array<String?>): Int {
        input.skipFully(4)                                          // max_stack, max_locals
        input.skipFully(input.readInt())                            // bytecode
        input.skipFully(input.readUnsignedShort() * 8)              // exception table

        var min = Int.MAX_VALUE
        repeat(input.readUnsignedShort()) {
            val name = utf8[input.readUnsignedShort()]
            val length = input.readInt()
            if (name == "LineNumberTable") {
                repeat(input.readUnsignedShort()) {
                    input.skipFully(2)
                    min = minOf(min, input.readUnsignedShort())
                }
            } else {
                input.skipFully(length)
            }
        }
        return min
    }

    private fun DataInputStream.skipFully(n: Int) {
        skipNBytes(n.toLong())
    }
}
