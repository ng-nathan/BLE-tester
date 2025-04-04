package com.bleproject.bletester

/**
 * Parser for Bluetooth LE Advertising data according to Bluetooth Core Specification
 */
class BleAdvertisingParser {

    data class AdvertisingDataItem(
        val length: Int,
        val type: String,
        val typeCode: Int,
        val value: String,
        val textValue: String? = null
    )

    companion object {
        // Advertising Data Type names based on Bluetooth SIG specifications
        private val ADV_TYPES = mapOf(
            0x01 to "Flags",
            0x02 to "Incomplete List of 16-bit Service UUIDs",
            0x03 to "Complete List of 16-bit Service UUIDs",
            0x04 to "Incomplete List of 32-bit Service UUIDs",
            0x05 to "Complete List of 32-bit Service UUIDs",
            0x06 to "Incomplete List of 128-bit Service UUIDs",
            0x07 to "Complete List of 128-bit Service UUIDs",
            0x08 to "Shortened Local Name",
            0x09 to "Complete Local Name",
            0x0A to "TX Power Level",
            0x0D to "Class of Device",
            0x0E to "Simple Pairing Hash C",
            0x0F to "Simple Pairing Randomizer R",
            0x10 to "Device ID",
            0x16 to "Service Data - 16-bit UUID",
            0x1F to "List of 32-bit Service Solicitation UUIDs",
            0x20 to "Service Data - 32-bit UUID",
            0x21 to "Service Data - 128-bit UUID",
            0x24 to "URI",
            0x25 to "Indoor Positioning",
            0x26 to "Transport Discovery Data",
            0x27 to "LE Supported Features",
            0x28 to "Channel Map Update Indication",
            0x29 to "PB-ADV",
            0x2A to "Mesh Message",
            0x2B to "Mesh Beacon",
            0x2C to "BIGInfo",
            0x2D to "Broadcast_Code",
            0xFF to "Manufacturer Specific Data"
        )

        /**
         * Parse raw advertising data into structured segments
         * @param rawData The byte array of raw advertising data
         * @return List of parsed advertising data items
         */
        fun parseAdvertisingData(rawData: ByteArray): List<AdvertisingDataItem> {
            val dataItems = mutableListOf<AdvertisingDataItem>()
            var index = 0

            while (index < rawData.size) {
                // First byte is the length (including type)
                val length = rawData[index].toInt() and 0xFF

                // If length is 0 or would exceed the packet, stop parsing
                if (length == 0 || index + length >= rawData.size) {
                    break
                }

                // Second byte is the type
                val type = rawData[index + 1].toInt() and 0xFF

                // The rest is the data value
                val valueBytes = ByteArray(length - 1)
                System.arraycopy(rawData, index + 2, valueBytes, 0, length - 1)

                // Convert value to hex string
                val valueHex = valueBytes.joinToString("") { byte ->
                    String.format("%02X", byte)
                }

                // Get type name
                val typeName = ADV_TYPES[type] ?: "Unknown (0x${String.format("%02X", type)})"

                // Try to parse data as UTF-8 if it's likely to be text
                var textValue: String? = null
                if ((type == 0x08 || type == 0x09) && valueBytes.isNotEmpty()) {  // Shortened or Complete Local Name
                    try {
                        textValue = String(valueBytes, Charsets.UTF_8).trim()
                    } catch (e: Exception) {
                        // Ignore conversion errors
                    }
                }

                dataItems.add(
                    AdvertisingDataItem(
                        length = length,
                        type = typeName,
                        typeCode = type,
                        value = "0x$valueHex",
                        textValue = textValue
                    )
                )

                // Move to the next data structure
                index += length + 1
            }

            return dataItems
        }

        /**
         * Converts hex string to byte array
         */
        fun hexStringToByteArray(hexString: String): ByteArray {
            // Remove "0x" prefix if present and whitespace
            val cleanHex = hexString.replace("0x", "").replace("\\s".toRegex(), "")

            // Must have even length
            if (cleanHex.length % 2 != 0) {
                throw IllegalArgumentException("Hex string must have an even length")
            }

            val len = cleanHex.length / 2
            val data = ByteArray(len)

            for (i in 0 until len) {
                val index = i * 2
                data[i] = cleanHex.substring(index, index + 2).toInt(16).toByte()
            }

            return data
        }
    }
}