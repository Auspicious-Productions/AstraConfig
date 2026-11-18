package xyz.essencexc.astra.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.essencexc.astra.processor.AstraProcessor
import java.io.StringWriter
import java.net.URI
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

/** The annotation processor reports Java mistakes at compile time. */
class ProcessorTest {
    private fun compile(className: String, source: String): List<String> {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val file = object : SimpleJavaFileObject(URI.create("string:///$className.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean) = source
        }
        val out = Files.createTempDirectory("astra-processor-test")
        val options = listOf("-classpath", System.getProperty("java.class.path"), "-d", out.toString(), "-proc:only")
        val task = compiler.getTask(StringWriter(), null, diagnostics, options, null, listOf(file))
        task.setProcessors(listOf(AstraProcessor()))
        task.call()
        return diagnostics.diagnostics.filter { it.kind == javax.tools.Diagnostic.Kind.ERROR }.map { it.getMessage(null) }
    }

    @Test
    fun `reports bad annotations`() {
        val errors = compile("BadModule",
            """
            import xyz.essencexc.astra.api.annotations.*;
            public class BadModule {
                @Switch(title = "A") public static int notBoolean = 0;
                @Slider(title = "B", min = 10, max = 5) public static int backwards = 7;
                @Slider(title = "C", min = 0, max = 100, logarithmic = true) public static int logFromZero = 1;
                @Dropdown(title = "D") public static int noOptions = 0;
                @Switch(title = "E") public static final boolean frozen = false;
                @Text(title = "F") @DependsOn("missing") public static String text = "";
                @Switch(title = "G") @Checkbox(title = "H") public static boolean twice = false;
                @Button(title = "I") private void click(int x) {}
            }
            """.trimIndent(),
        )
        val all = errors.joinToString("\n")
        assertTrue(all.contains("'BadModule.notBoolean'") && all.contains("expected boolean"), all)
        assertTrue(all.contains("'BadModule.backwards' min must be below max"), all)
        assertTrue(all.contains("'BadModule.logFromZero'") && all.contains("logarithmic"), all)
        assertTrue(all.contains("'BadModule.noOptions'") && all.contains("without options"), all)
        assertTrue(all.contains("'BadModule.frozen' is final"), all)
        assertTrue(all.contains("@DependsOn(\"missing\")"), all)
        assertTrue(all.contains("'BadModule.twice' has more than one option annotation"), all)
        assertTrue(all.contains("'BadModule.click' is a @Button"), all)
    }

    @Test
    fun `accepts a correct module`() {
        val errors = compile("GoodModule",
            """
            import xyz.essencexc.astra.api.annotations.*;
            public class GoodModule {
                @Switch(title = "A") public static boolean on = true;
                @Slider(title = "B", min = 1, max = 100000, logarithmic = true) public static int range = 10;
                @Dropdown(title = "C", options = {"x", "y"}) public static int pick = 0;
                @Text(title = "D") @DependsOn("on") public static String text = "";
                @Button(title = "E") private void click() {}
            }
            """.trimIndent(),
        )
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }
}
