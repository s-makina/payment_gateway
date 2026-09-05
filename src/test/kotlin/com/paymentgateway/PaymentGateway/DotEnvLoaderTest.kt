package com.paymentgateway.PaymentGateway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DotEnvLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parses key-value pairs, comments, exports and quotes`() {
        val envFile = tempDir.resolve(".env")
        Files.writeString(
            envFile,
            """
            # a comment
            ONEKHUSA_CAPTURED_BY=user@example.com
            export QUOTED_DOUBLE="hello world"
            QUOTED_SINGLE='single quoted'
            WITH_COMMENT=value # trailing comment
            EMPTY=
            MALFORMED_LINE_WITHOUT_EQUALS
            """.trimIndent()
        )

        val parsed = DotEnvLoader.parse(envFile)

        assertEquals("user@example.com", parsed["ONEKHUSA_CAPTURED_BY"])
        assertEquals("hello world", parsed["QUOTED_DOUBLE"])
        assertEquals("single quoted", parsed["QUOTED_SINGLE"])
        assertEquals("value", parsed["WITH_COMMENT"])
        assertEquals("", parsed["EMPTY"])
        assertEquals(5, parsed.size)
    }
}
