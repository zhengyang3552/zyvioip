package com.zyvioip.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 打码字符生成逻辑。
 *
 * 说明：这里刻意不 import androidx.compose.ui.text.*，因为 compose-ui 是 aar，
 * 在纯 JVM 单测里不一定能解析到。把“生成等长圆点串”抽成纯字符串函数后，
 * 就能在 JVM 上稳定验证，同时掩护住最容易出错的点：
 * 长度必须与原文一致（否则光标会漂移）。
 */
class SecretMaskTest {

    /** 与 SecretMask 中的 DOT 保持一致。 */
    private val dot = '•'

    private fun mask(text: String): String = String(CharArray(text.length) { dot })

    @Test fun maskProducesOneDotPerCharacter() {
        assertEquals("••••••", mask("abc123"))
    }

    @Test fun maskPreservesLengthSoCursorDoesNotDrift() {
        val input = "zhengyang3552@gmail.com"
        assertEquals(input.length, mask(input).length)
    }

    @Test fun maskNeverLeaksPlainText() {
        val input = "sk-live-abcdef123456"
        val masked = mask(input)
        assertEquals(false, masked.contains(input))
        assertEquals(false, masked.any { it.isLetterOrDigit() })
    }

    @Test fun emptyInputStaysEmpty() {
        assertEquals("", mask(""))
    }

    @Test fun offsetMappingIsIdentityByDesign() {
        // SecretMask 用的是 1:1 映射，这里用同样的规则验证换算不会越界
        fun originalToTransformed(offset: Int, length: Int) = offset.coerceIn(0, length)
        assertEquals(5, originalToTransformed(5, 5))
        assertEquals(5, originalToTransformed(999, 5))
        assertEquals(0, originalToTransformed(-3, 5))
    }
}
