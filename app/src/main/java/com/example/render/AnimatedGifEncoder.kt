package com.example.render

import android.graphics.Bitmap
import java.io.IOException
import java.io.OutputStream
import java.util.HashMap
import kotlin.math.abs

/**
 * A simple, self-contained Kotlin implementation of Kevin Weiner's Animated GIF Encoder.
 * It quantizes 24-bit RGB pixels into a 256-color palette and compresses the output
 * using the standard GIF LZW compression algorithm.
 */
class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var delay = 0
    private var repeat = -1 // -1 = no repeat, 0 = infinite loop
    private var out: OutputStream? = null
    private var currentFrame: Bitmap? = null
    private var pixels: ByteArray? = null // Bounded 8-bit indices
    private var colorTab: ByteArray? = null // RGB palette
    private var usedEntry = BooleanArray(256)
    private var palSize = 7 // palette size exponent (2^(palSize+1) = 256 colors)
    private var firstFrame = true

    fun setDelay(ms: Int) {
        delay = Math.round(ms / 10.0f) * 10
    }

    fun setRepeat(iter: Int) {
        if (iter >= 0) repeat = iter
    }

    fun start(os: OutputStream): Boolean {
        out = os
        try {
            writeHeader()
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun addFrame(im: Bitmap): Boolean {
        if (im == null || out == null) return false
        try {
            if (firstFrame) {
                width = im.width
                height = im.height
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            currentFrame = im
            getImagePixels()
            analyzePixels()
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun finish(): Boolean {
        if (out == null) return false
        var ok = true
        try {
            out!!.write(0x3b) // GIF trailer
            out!!.flush()
            out!!.close()
        } catch (e: IOException) {
            ok = false
        }
        out = null
        currentFrame = null
        pixels = null
        colorTab = null
        firstFrame = true
        return ok
    }

    private fun getImagePixels() {
        val w = currentFrame!!.width
        val h = currentFrame!!.height
        val intPixels = IntArray(w * h)
        currentFrame!!.getPixels(intPixels, 0, w, 0, 0, w, h)
        pixels = ByteArray(w * h)
        
        // Simple octree/median-cut approximation palette analyzer
        val colorMap = HashMap<Int, Int>()
        for (i in intPixels.indices) {
            val c = intPixels[i]
            // Mask lower bits to quantize down to roughly 256 colors
            val quantized = c and 0xF0F0F0F0.toInt()
            colorMap[quantized] = (colorMap[quantized] ?: 0) + 1
        }

        val sortedColors = colorMap.entries.sortedByDescending { it.value }.take(256)
        val palette = ByteArray(768)
        for (i in 0 until 256) {
            if (i < sortedColors.size) {
                val c = sortedColors[i].key
                palette[i * 3] = ((c shr 16) and 0xFF).toByte()
                palette[i * 3 + 1] = ((c shr 8) and 0xFF).toByte()
                palette[i * 3 + 2] = (c and 0xFF).toByte()
            } else {
                palette[i * 3] = 0
                palette[i * 3 + 1] = 0
                palette[i * 3 + 2] = 0
            }
        }
        colorTab = palette

        // Map pixels to palette indices
        for (i in intPixels.indices) {
            val c = intPixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            
            var minDistance = Int.MAX_VALUE
            var bestIndex = 0
            // Fast nearest-color search
            for (idx in 0 until sortedColors.size) {
                val pr = palette[idx * 3].toInt() and 0xFF
                val pg = palette[idx * 3 + 1].toInt() and 0xFF
                val pb = palette[idx * 3 + 2].toInt() and 0xFF
                val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
                if (dist < minDistance) {
                    minDistance = dist
                    bestIndex = idx
                }
            }
            pixels!![i] = bestIndex.toByte()
        }
    }

    private fun analyzePixels() {
        usedEntry.fill(false)
        for (b in pixels!!) {
            usedEntry[b.toInt() and 0xFF] = true
        }
    }

    private fun writeHeader() {
        out!!.write("GIF89a".toByteArray())
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out!!.write(0x80 or 0x70 or palSize) // Global Color Table Flag, 8-bit color
        out!!.write(0) // Background Color Index
        out!!.write(0) // Pixel Aspect Ratio
    }

    private fun writePalette() {
        out!!.write(colorTab!!)
    }

    private fun writeNetscapeExt() {
        out!!.write(0x21) // Extension Introducer
        out!!.write(0xff) // Application Extension Label
        out!!.write(11) // Block Size
        out!!.write("NETSCAPE2.0".toByteArray())
        out!!.write(3) // Sub-block Size
        out!!.write(1) // Loop Type
        writeShort(repeat) // Loop Count
        out!!.write(0) // Block Terminator
    }

    private fun writeGraphicCtrlExt() {
        out!!.write(0x21) // Extension Introducer
        out!!.write(0xf9) // Graphic Control Label
        out!!.write(4) // Block Size
        out!!.write(0x00) // Transparent Color Flag, Disposal Method (0 = unspecified)
        writeShort(delay / 10) // Frame Delay (hundredths of a second)
        out!!.write(0) // Transparent Color Index
        out!!.write(0) // Block Terminator
    }

    private fun writeImageDesc() {
        out!!.write(0x2c) // Image Separator
        writeShort(0) // Left Position
        writeShort(0) // Top Position
        writeShort(width)
        writeShort(height)
        if (firstFrame) {
            out!!.write(0) // No Local Color Table
        } else {
            out!!.write(0x80 or palSize) // Use Local Color Table
        }
    }

    private fun writePixels() {
        val lzw = LzwEncoder(width, height, pixels!!, palSize + 1)
        lzw.encode(out!!)
    }

    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }
}

/**
 * Companion LZW Compression class for encoding the pixel byte index stream into GIF LZW format.
 */
private class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val initCodeSize: Int
) {
    private val gInitBits = initCodeSize
    private var numPixels = imgW * imgH
    private var accum = ByteArray(256)
    private var aCount = 0

    fun encode(os: OutputStream) {
        os.write(gInitBits) // Write initial code size
        
        var remaining = numPixels
        var curPixel = 0

        val maxBits = 12
        val maxMaxCode = 1 shl maxBits
        val htab = IntArray(5003) { -1 }
        val codetab = IntArray(5003)
        val hsize = 5003

        var nBits = gInitBits + 1
        var maxcode = (1 shl nBits) - 1
        val clearCode = 1 shl gInitBits
        val eofCode = clearCode + 1
        var freeCode = clearCode + 2

        aCount = 0
        var curAccum = 0
        var curBits = 0

        fun charOut(c: Byte) {
            accum[aCount++] = c
            if (aCount >= 254) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }

        fun flush() {
            if (aCount > 0) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }

        fun writeCode(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += nBits
            while (curBits >= 8) {
                charOut((curAccum and 0xff).toByte())
                curAccum = curAccum ushr 8
                curBits -= 8
            }
            if (freeCode > maxcode || code == clearCode) {
                if (code == clearCode) {
                    nBits = gInitBits + 1
                    maxcode = (1 shl nBits) - 1
                } else {
                    nBits++
                    maxcode = if (nBits == maxBits) {
                        maxMaxCode
                    } else {
                        (1 shl nBits) - 1
                    }
                }
            }
            if (code == eofCode) {
                while (curBits > 0) {
                    charOut((curAccum and 0xff).toByte())
                    curAccum = curAccum ushr 8
                    curBits -= 8
                }
                flush()
            }
        }

        writeCode(clearCode)

        var ent = pixAry[curPixel++].toInt() and 0xff
        remaining--

        while (remaining > 0) {
            val c = pixAry[curPixel++].toInt() and 0xff
            remaining--
            val fcode = (c shl maxBits) + ent
            var i = (c shl 4) xor ent // Quadratic double hashing
            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsize - i
                if (i == 0) disp = 1
                var found = false
                while (true) {
                    i -= disp
                    if (i < 0) i += hsize
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        found = true
                        break
                    }
                    if (htab[i] < 0) break
                }
                if (found) continue
            }
            writeCode(ent)
            ent = c
            if (freeCode < maxMaxCode) {
                codetab[i] = freeCode++
                htab[i] = fcode
            } else {
                htab.fill(-1)
                freeCode = clearCode + 2
                writeCode(clearCode)
            }
        }
        writeCode(ent)
        writeCode(eofCode)
        os.write(0) // Write block terminator
    }
}
