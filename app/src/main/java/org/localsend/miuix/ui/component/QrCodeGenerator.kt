package org.localsend.miuix.ui.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 纯 Kotlin 极简 QR 码生成器与 Compose 渲染组件。
 * 零第三方依赖，支持将局域网 HTTP/HTTPS 链接生成高清晰度二维码。
 */
object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = AndroidColor.BLACK,
        lightColor: Int = AndroidColor.WHITE
    ): Bitmap {
        val matrix = SimpleQrEncoder.encode(content)
        val matrixSize = matrix.size
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val scale = sizePx.toFloat() / matrixSize

        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val my = (y / scale).toInt().coerceIn(0, matrixSize - 1)
            for (x in 0 until sizePx) {
                val mx = (x / scale).toInt().coerceIn(0, matrixSize - 1)
                pixels[y * sizePx + x] = if (matrix[my][mx]) darkColor else lightColor
            }
        }
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }
}

@Composable
fun QrCodeImage(
    content: String,
    size: Dp = 200.dp,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val darkArgb = darkColor.toArgb()
    val lightArgb = lightColor.toArgb()
    val bitmap = remember(content, darkArgb, lightArgb) {
        QrCodeGenerator.generateQrBitmap(
            content = content,
            sizePx = 512,
            darkColor = darkArgb,
            lightColor = lightArgb
        )
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = modifier.size(size)
    )
}

/**
 * 轻量级标准 QR Code 编码器（支持 Version 1-10 Byte Mode）
 */
private object SimpleQrEncoder {

    fun encode(text: String): Array<BooleanArray> {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = getMinVersion(data.size)
        val moduleCount = version * 4 + 17
        val matrix = Array(moduleCount) { BooleanArray(moduleCount) }
        val isFunction = Array(moduleCount) { BooleanArray(moduleCount) }

        // 1. Finder Patterns
        drawFinderPattern(matrix, isFunction, 0, 0)
        drawFinderPattern(matrix, isFunction, moduleCount - 7, 0)
        drawFinderPattern(matrix, isFunction, 0, moduleCount - 7)

        // 2. Alignment Patterns
        if (version >= 2) {
            val pos = getAlignmentPatternPositions(version)
            for (r in pos) {
                for (c in pos) {
                    if (isFunction[r][c]) continue
                    drawAlignmentPattern(matrix, isFunction, r - 2, c - 2)
                }
            }
        }

        // 3. Timing Patterns
        for (i in 8 until moduleCount - 8) {
            val bit = (i % 2 == 0)
            if (!isFunction[6][i]) {
                matrix[6][i] = bit
                isFunction[6][i] = true
            }
            if (!isFunction[i][6]) {
                matrix[i][6] = bit
                isFunction[i][6] = true
            }
        }

        // 4. Dark Module
        matrix[4 * version + 9][8] = true
        isFunction[4 * version + 9][8] = true

        // 5. Encode Payload with ECC
        val codewords = encodeData(data, version)

        // 6. Place Data bits into Matrix
        placeDataBits(matrix, isFunction, codewords, version)

        // 7. Apply Mask (Mask 0: (x + y) % 2 == 0)
        applyMask(matrix, isFunction)

        // 8. Format Information (Mask 0 + ECC Level M)
        drawFormatInfo(matrix, isFunction)

        // 9. Add Quiet Zone (Border of 2 modules)
        val border = 2
        val finalSize = moduleCount + border * 2
        val finalMatrix = Array(finalSize) { BooleanArray(finalSize) }
        for (r in 0 until moduleCount) {
            for (c in 0 until moduleCount) {
                finalMatrix[r + border][c + border] = matrix[r][c]
            }
        }

        return finalMatrix
    }

    private fun getMinVersion(dataLen: Int): Int = when {
        dataLen <= 14 -> 1
        dataLen <= 26 -> 2
        dataLen <= 42 -> 3
        dataLen <= 62 -> 4
        dataLen <= 84 -> 5
        dataLen <= 106 -> 6
        dataLen <= 122 -> 7
        dataLen <= 152 -> 8
        dataLen <= 180 -> 9
        else -> 10
    }

    private fun drawFinderPattern(m: Array<BooleanArray>, f: Array<BooleanArray>, r: Int, c: Int) {
        for (y in -1..7) {
            for (x in -1..7) {
                val my = r + y
                val mx = c + x
                if (my in m.indices && mx in m.indices) {
                    val isDark = (y in 0..6 && (x == 0 || x == 6)) ||
                        (x in 0..6 && (y == 0 || y == 6)) ||
                        (y in 2..4 && x in 2..4)
                    m[my][mx] = isDark
                    f[my][mx] = true
                }
            }
        }
    }

    private fun drawAlignmentPattern(m: Array<BooleanArray>, f: Array<BooleanArray>, r: Int, c: Int) {
        for (y in 0..4) {
            for (x in 0..4) {
                val isDark = y == 0 || y == 4 || x == 0 || x == 4 || (y == 2 && x == 2)
                m[r + y][c + x] = isDark
                f[r + y][c + x] = true
            }
        }
    }

    private fun getAlignmentPatternPositions(version: Int): IntArray = when (version) {
        2 -> intArrayOf(6, 18)
        3 -> intArrayOf(6, 22)
        4 -> intArrayOf(6, 26)
        5 -> intArrayOf(6, 30)
        6 -> intArrayOf(6, 34)
        7 -> intArrayOf(6, 22, 38)
        8 -> intArrayOf(6, 24, 42)
        9 -> intArrayOf(6, 26, 46)
        10 -> intArrayOf(6, 28, 50)
        else -> intArrayOf(6, 18)
    }

    private val CAPACITY_DATA = intArrayOf(0, 16, 28, 44, 64, 86, 108, 124, 154, 182, 216)
    private val CAPACITY_ECC = intArrayOf(0, 10, 16, 26, 36, 48, 64, 72, 88, 110, 130)

    private fun encodeData(data: ByteArray, version: Int): IntArray {
        val totalDataCodewords = CAPACITY_DATA[version]
        val totalEccCodewords = CAPACITY_ECC[version]

        val bitBuffer = mutableListOf<Int>()

        // Mode Indicator: Byte (0100)
        bitBuffer.addAll(listOf(0, 1, 0, 0))

        // Character Count (8 bits for version 1-9)
        val count = data.size
        for (i in 7 downTo 0) {
            bitBuffer.add((count shr i) and 1)
        }

        // Data Bytes
        for (b in data) {
            val v = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bitBuffer.add((v shr i) and 1)
            }
        }

        // Terminator (up to 4 zeroes)
        val maxBits = totalDataCodewords * 8
        val termLen = (maxBits - bitBuffer.size).coerceIn(0, 4)
        repeat(termLen) { bitBuffer.add(0) }

        // Pad to byte
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }

        // Pad Codewords (0xEC, 0x11)
        val pad = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < maxBits) {
            val p = pad[padIdx % 2]
            for (i in 7 downTo 0) {
                bitBuffer.add((p shr i) and 1)
            }
            padIdx++
        }

        // Convert bit buffer to data codewords
        val dataCodewords = IntArray(totalDataCodewords)
        for (i in 0 until totalDataCodewords) {
            var byteVal = 0
            for (b in 0..7) {
                byteVal = (byteVal shl 1) or bitBuffer[i * 8 + b]
            }
            dataCodewords[i] = byteVal
        }

        // Generate Reed-Solomon ECC
        val eccCodewords = generateReedSolomonEcc(dataCodewords, totalEccCodewords)

        return dataCodewords + eccCodewords
    }

    private fun generateReedSolomonEcc(data: IntArray, eccCount: Int): IntArray {
        val generator = getRsGeneratorPoly(eccCount)
        val info = IntArray(data.size + eccCount)
        System.arraycopy(data, 0, info, 0, data.size)

        for (i in data.indices) {
            val coef = info[i]
            if (coef != 0) {
                for (j in generator.indices) {
                    info[i + j] = info[i + j] xor gfMultiply(generator[j], coef)
                }
            }
        }

        val ecc = IntArray(eccCount)
        System.arraycopy(info, data.size, ecc, 0, eccCount)
        return ecc
    }

    private val EXP_TABLE = IntArray(512)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            EXP_TABLE[i + 255] = x
            LOG_TABLE[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 0x11D
        }
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]]
    }

    private fun getRsGeneratorPoly(deg: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until deg) {
            val next = intArrayOf(1, EXP_TABLE[i])
            val result = IntArray(poly.size + 1)
            for (p1 in poly.indices) {
                for (p2 in next.indices) {
                    result[p1 + p2] = result[p1 + p2] xor gfMultiply(poly[p1], next[p2])
                }
            }
            poly = result
        }
        return poly
    }

    private fun placeDataBits(m: Array<BooleanArray>, f: Array<BooleanArray>, data: IntArray, version: Int) {
        val size = m.size
        var byteIdx = 0
        var bitIdx = 7
        var up = true

        var x = size - 1
        while (x > 0) {
            if (x == 6) x-- // Skip vertical timing column
            val cols = intArrayOf(x, x - 1)
            val rows = if (up) (size - 1 downTo 0).toList() else (0 until size).toList()

            for (y in rows) {
                for (c in cols) {
                    if (!f[y][c]) {
                        val bit = if (byteIdx < data.size) {
                            ((data[byteIdx] shr bitIdx) and 1) == 1
                        } else false
                        m[y][c] = bit

                        bitIdx--
                        if (bitIdx < 0) {
                            bitIdx = 7
                            byteIdx++
                        }
                    }
                }
            }
            up = !up
            x -= 2
        }
    }

    private fun applyMask(m: Array<BooleanArray>, f: Array<BooleanArray>) {
        val size = m.size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!f[r][c]) {
                    if ((r + c) % 2 == 0) {
                        m[r][c] = !m[r][c]
                    }
                }
            }
        }
    }

    // Format Info bits for ECC Level M, Mask Pattern 0 (00 + 000 with BCH error correction and XOR mask)
    private val FORMAT_BITS = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)

    private fun drawFormatInfo(m: Array<BooleanArray>, f: Array<BooleanArray>) {
        val size = m.size
        // Top-left
        for (i in 0..5) m[8][i] = FORMAT_BITS[i] == 1
        m[8][7] = FORMAT_BITS[6] == 1
        m[8][8] = FORMAT_BITS[7] == 1
        m[7][8] = FORMAT_BITS[8] == 1
        for (i in 9..14) m[14 - i][8] = FORMAT_BITS[i] == 1

        // Top-right & Bottom-left
        for (i in 0..7) m[8][size - 1 - i] = FORMAT_BITS[i] == 1
        for (i in 8..14) m[size - 15 + i][8] = FORMAT_BITS[i] == 1
    }
}
