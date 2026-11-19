package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Decodes Android Binary XML (AXML) into readable, indented XML text.
 * Supports both UTF-8 and UTF-16LE string pools, namespaces, attributes, and typed values.
 */
object BinaryXmlDecoder {

    private const val CHUNK_AXML_FILE = 0x00080003
    private const val CHUNK_STRING_POOL = 0x001C0001
    private const val CHUNK_RESOURCE_IDS = 0x00080180
    private const val CHUNK_START_NAMESPACE = 0x00100100
    private const val CHUNK_END_NAMESPACE = 0x00100101
    private const val CHUNK_START_TAG = 0x00100102
    private const val CHUNK_END_TAG = 0x00100103
    private const val CHUNK_TEXT = 0x00100104

    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    fun isBinaryXml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return magic == CHUNK_AXML_FILE || (magic and 0xFFFF) == 0x0003
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (!isBinaryXml(bytes)) {
            return String(bytes, StandardCharsets.UTF_8)
        }

        return try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val fileType = buf.int
            if ((fileType and 0xFFFF) != 0x0003) {
                return String(bytes, StandardCharsets.UTF_8)
            }
            val fileSize = buf.int.coerceAtMost(bytes.size)

            var strings: List<String> = emptyList()
            val namespaces = mutableMapOf<String, String>()
            val pendingNsDeclarations = mutableListOf<Pair<String, String>>()
            val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            var indent = 0

            while (buf.position() + 8 <= fileSize) {
                val chunkStart = buf.position()
                val chunkType = buf.int
                val chunkSize = buf.int
                if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) break

                when (chunkType) {
                    CHUNK_STRING_POOL, 0x0001 -> {
                        strings = parseStringPool(bytes, chunkStart, chunkSize)
                    }
                    CHUNK_RESOURCE_IDS -> {
                        // Resource ID map; skipped for display
                    }
                    CHUNK_START_NAMESPACE -> {
                        if (chunkSize >= 24) {
                            buf.int // lineNumber
                            buf.int // comment
                            val prefixIdx = buf.int
                            val uriIdx = buf.int
                            val prefix = strings.getOrNull(prefixIdx) ?: "ns"
                            val uri = strings.getOrNull(uriIdx) ?: ""
                            if (uri.isNotEmpty()) {
                                namespaces[uri] = prefix
                                pendingNsDeclarations.add(prefix to uri)
                            }
                        }
                    }
                    CHUNK_END_NAMESPACE -> {
                        // Nothing to emit
                    }
                    CHUNK_START_TAG -> {
                        if (chunkSize >= 36) {
                            buf.int // lineNumber
                            buf.int // comment
                            val nsIdx = buf.int
                            val nameIdx = buf.int
                            val attrStart = buf.short.toInt() and 0xFFFF
                            val attrSize = buf.short.toInt() and 0xFFFF
                            val attrCount = buf.short.toInt() and 0xFFFF
                            buf.short // idIndex
                            buf.short // classIndex
                            buf.short // styleIndex

                            val tagName = strings.getOrNull(nameIdx) ?: "tag"
                            val tagNs = if (nsIdx >= 0) strings.getOrNull(nsIdx) else null
                            val tagPrefix = tagNs?.let { namespaces[it] }
                            val fullTag = if (!tagPrefix.isNullOrEmpty()) "$tagPrefix:$tagName" else tagName

                            appendIndent(out, indent)
                            out.append("<").append(fullTag)

                            if (pendingNsDeclarations.isNotEmpty()) {
                                for ((prefix, uri) in pendingNsDeclarations) {
                                    out.append(" xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append("\"")
                                }
                                pendingNsDeclarations.clear()
                            }

                            val attrOffset = chunkStart + 16 + attrStart
                            val stride = if (attrSize >= 20) attrSize else 20
                            for (i in 0 until attrCount) {
                                val pos = attrOffset + i * stride
                                if (pos + 20 > chunkStart + chunkSize) break
                                buf.position(pos)
                                val attrNsIdx = buf.int
                                val attrNameIdx = buf.int
                                val attrRawValIdx = buf.int
                                val valSize = buf.short.toInt() and 0xFFFF
                                buf.get() // res0
                                val valType = buf.get().toInt() and 0xFF
                                val valData = buf.int

                                val attrName = strings.getOrNull(attrNameIdx) ?: "attr_$i"
                                val attrNs = if (attrNsIdx >= 0) strings.getOrNull(attrNsIdx) else null
                                val attrPrefix = attrNs?.let { namespaces[it] }
                                val fullAttr = if (!attrPrefix.isNullOrEmpty()) "$attrPrefix:$attrName" else attrName

                                val valueStr = formatAttrValue(
                                    valType = valType,
                                    valData = valData,
                                    rawIdx = attrRawValIdx,
                                    strings = strings,
                                    attrName = attrName
                                )

                                if (attrCount > 2) {
                                    out.append("\n")
                                    appendIndent(out, indent + 1)
                                } else {
                                    out.append(" ")
                                }
                                out.append(fullAttr).append("=\"").append(escapeXml(valueStr)).append("\"")
                            }

                            out.append(">\n")
                            indent++
                        }
                    }
                    CHUNK_END_TAG -> {
                        if (chunkSize >= 24) {
                            buf.int // lineNumber
                            buf.int // comment
                            val nsIdx = buf.int
                            val nameIdx = buf.int
                            val tagName = strings.getOrNull(nameIdx) ?: "tag"
                            val tagNs = if (nsIdx >= 0) strings.getOrNull(nsIdx) else null
                            val tagPrefix = tagNs?.let { namespaces[it] }
                            val fullTag = if (!tagPrefix.isNullOrEmpty()) "$tagPrefix:$tagName" else tagName
                            indent = (indent - 1).coerceAtLeast(0)
                            appendIndent(out, indent)
                            out.append("</").append(fullTag).append(">\n")
                        }
                    }
                    CHUNK_TEXT -> {
                        if (chunkSize >= 28) {
                            buf.int // lineNumber
                            buf.int // comment
                            val textIdx = buf.int
                            val text = strings.getOrNull(textIdx)
                            if (!text.isNullOrBlank()) {
                                appendIndent(out, indent)
                                out.append(escapeXml(text.trim())).append("\n")
                            }
                        }
                    }
                }
                buf.position(chunkStart + chunkSize)
            }

            out.toString()
        } catch (e: Exception) {
            "<!-- Failed to fully decode binary XML: ${e.message} -->\n"
        }
    }

    private fun parseStringPool(bytes: ByteArray, chunkStart: Int, chunkSize: Int): List<String> {
        val buf = ByteBuffer.wrap(bytes, chunkStart, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // type
        buf.int // size
        val stringCount = buf.int
        buf.int // styleCount
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart

        val isUtf8 = (flags and (1 shl 8)) != 0
        val offsets = IntArray(stringCount) { buf.int }
        val poolBase = chunkStart + stringsStart
        val result = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            var pos = poolBase + offsets[i]
            if (pos < 0 || pos >= bytes.size) {
                result.add("")
                continue
            }
            if (isUtf8) {
                // UTF-16 length (1 or 2 bytes)
                val u16len = bytes[pos].toInt() and 0xFF
                pos += if ((u16len and 0x80) != 0) 2 else 1
                if (pos >= bytes.size) {
                    result.add("")
                    continue
                }
                var u8len = bytes[pos].toInt() and 0xFF
                if ((u8len and 0x80) != 0 && pos + 1 < bytes.size) {
                    u8len = ((u8len and 0x7F) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                    pos += 2
                } else {
                    pos += 1
                }
                val safeLen = u8len.coerceIn(0, (bytes.size - pos).coerceAtLeast(0))
                result.add(String(bytes, pos, safeLen, StandardCharsets.UTF_8))
            } else {
                if (pos + 2 > bytes.size) {
                    result.add("")
                    continue
                }
                var len = ((bytes[pos + 1].toInt() and 0xFF) shl 8) or (bytes[pos].toInt() and 0xFF)
                if ((len and 0x8000) != 0 && pos + 4 <= bytes.size) {
                    val low = ((bytes[pos + 3].toInt() and 0xFF) shl 8) or (bytes[pos + 2].toInt() and 0xFF)
                    len = ((len and 0x7FFF) shl 16) or low
                    pos += 4
                } else {
                    pos += 2
                }
                val byteLen = (len * 2).coerceIn(0, (bytes.size - pos).coerceAtLeast(0))
                result.add(String(bytes, pos, byteLen, StandardCharsets.UTF_16LE))
            }
        }
        return result
    }

    private fun formatAttrValue(
        valType: Int,
        valData: Int,
        rawIdx: Int,
        strings: List<String>,
        attrName: String
    ): String {
        if (rawIdx >= 0 && rawIdx < strings.size) {
            return strings[rawIdx]
        }
        return when (valType) {
            TYPE_STRING -> strings.getOrNull(valData) ?: ""
            TYPE_REFERENCE -> "@0x${Integer.toHexString(valData)}"
            TYPE_ATTRIBUTE -> "?0x${Integer.toHexString(valData)}"
            TYPE_INT_BOOLEAN -> if (valData != 0) "true" else "false"
            TYPE_INT_DEC -> valData.toString()
            TYPE_INT_HEX -> "0x${Integer.toHexString(valData)}"
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(valData).toString()
            TYPE_DIMENSION -> decodeComplex(valData, isFraction = false)
            TYPE_FRACTION -> decodeComplex(valData, isFraction = true)
            TYPE_NULL -> ""
            else -> if (attrName.contains("SdkVersion", ignoreCase = true)) valData.toString()
            else "0x${Integer.toHexString(valData)}"
        }
    }

    private fun decodeComplex(data: Int, isFraction: Boolean): String {
        val units = if (isFraction) {
            arrayOf("%", "%p")
        } else {
            arrayOf("px", "dp", "sp", "pt", "in", "mm")
        }
        val unitIdx = data and 0x0F
        val unit = units.getOrElse(unitIdx) { "" }
        val mantissa = (data shr 8)
        return "$mantissa$unit"
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) {
        repeat(indent.coerceAtMost(20)) { sb.append("  ") }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
