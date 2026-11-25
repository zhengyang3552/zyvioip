package com.zyvioip.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 显式打码：把每个字符替换成固定数量的圆点。
 *
 * 不用 PasswordVisualTransformation 的原因：它依赖字体的 bullet 字形，
 * 在部分国产 ROM / 自定义字体下会渲染成空白（看起来像“什么都没显示”），
 * 用户会以为密钥没保存、或者“显示密钥”开关失效。
 * 这里用普通字符，任何字体都能正常显示；同时保持 1:1 长度映射，
 * 光标位置与选区都不会错位。
 */
object SecretMask : VisualTransformation {
    private const val DOT = '•'

    override fun filter(text: AnnotatedString): TransformedText {
        val masked = AnnotatedString(String(CharArray(text.text.length) { DOT }))
        return TransformedText(
            masked,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, text.text.length)
                override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, text.text.length)
            },
        )
    }
}
