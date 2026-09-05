package com.paymentgateway.PaymentGateway

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Minimal `.env` file support. Spring Boot does not read `.env` files
 * natively, so values placed there (e.g. `ONEKHUSA_CAPTURED_BY`) would
 * otherwise be silently ignored. Entries are applied as system properties
 * only when not already defined — real environment variables and `-D`
 * flags always take precedence.
 */
object DotEnvLoader {

    private val log = LoggerFactory.getLogger(DotEnvLoader::class.java)

    fun load() {
        parse(Paths.get(".env").takeIf { Files.isRegularFile(it) } ?: return)
            .forEach { (key, value) ->
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value)
                }
            }
        log.info(".env file loaded")
    }

    internal fun parse(path: java.nio.file.Path): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        Files.readAllLines(path).forEachIndexed { index, rawLine ->
            // Strip a UTF-8 BOM if the editor wrote one on the first line.
            val line = if (index == 0) rawLine.trim().removePrefix("\uFEFF") else rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val withoutExport = line.removePrefix("export").trim()
            val separator = withoutExport.indexOf('=')
            if (separator <= 0) {
                log.warn("Ignoring malformed line {} in .env", index + 1)
                return@forEachIndexed
            }
            val key = withoutExport.substring(0, separator).trim()
            var value = withoutExport.substring(separator + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
            } else if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length - 1)
            } else {
                val commentStart = value.indexOf(" #")
                if (commentStart >= 0) value = value.substring(0, commentStart).trim()
            }
            if (key.isNotEmpty()) entries[key] = value
        }
        return entries
    }
}
