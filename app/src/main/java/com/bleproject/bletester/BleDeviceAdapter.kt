package com.bleproject.bletester

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.graphics.toColorInt

class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isExtended: Boolean,
    val manufacturerData: String,
    val rawPayload: String,
    var packetCount: Int = 1  // Initialize with 1 since creation means we received 1 packet
)

class BleDeviceAdapter(context: Context, devices: ArrayList<BleDevice>) :
    ArrayAdapter<BleDevice>(context, 0, devices) {

    // Helper function to make text bold
    private fun String.bold(): SpannableString {
        val spannable = SpannableString(this)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            this.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.device_list_item, parent, false)

            // Add border programmatically
            val gd = GradientDrawable()
            gd.setColor(Color.WHITE)
            gd.setStroke(1, "#DDDDDD".toColorInt())
            gd.cornerRadius = 8f * context.resources.displayMetrics.density
            itemView.background = gd
        }

        val device = getItem(position)
        if (device != null) {
            val deviceNameTextView = itemView!!.findViewById<TextView>(R.id.deviceNameTextView)
            val rssiTextView = itemView.findViewById<TextView>(R.id.rssiTextView)
            val advertisingTypeTextView = itemView.findViewById<TextView>(R.id.advertisingTypeTextView)
            val manufacturerDataTextView = itemView.findViewById<TextView>(R.id.manufacturerDataTextView)
            val rawPayloadTextView = itemView.findViewById<TextView>(R.id.rawPayloadTextView)
            val payloadSizeTextView = itemView.findViewById<TextView>(R.id.payloadSizeTextView)
            val packetCountTextView = itemView.findViewById<TextView>(R.id.packetCountTextView)
            val advertisingDataTable = itemView.findViewById<TableLayout>(R.id.advertisingDataTable)

            deviceNameTextView.text = device.name
            rssiTextView.text = "${device.rssi} dBm"
            advertisingTypeTextView.text = if (device.isExtended) "Extended" else "Legacy"
            manufacturerDataTextView.text = device.manufacturerData
            rawPayloadTextView.text = device.rawPayload
            packetCountTextView.text = device.packetCount.toString()

            // Calculate and display payload size
            try {
                val rawHex = device.rawPayload.replace("0x", "").replace(" ", "")
                val payloadSizeBytes = rawHex.length / 2
                payloadSizeTextView.text = "Size: $payloadSizeBytes bytes"
            } catch (e: Exception) {
                payloadSizeTextView.text = "Size: Unknown"
            }

            // Parse and display the advertising data in the table
            populateAdvertisingDataTable(advertisingDataTable, device.rawPayload)
        }

        return itemView!!
    }

    private fun populateAdvertisingDataTable(tableLayout: TableLayout, rawPayloadHex: String) {
        // Clear existing rows except the header row
        if (tableLayout.childCount > 1) {
            tableLayout.removeViews(1, tableLayout.childCount - 1)
        }

        try {
            // Try to parse the hex string to byte array
            val rawBytes = BleAdvertisingParser.hexStringToByteArray(rawPayloadHex)

            // Parse the advertising data
            val dataItems = BleAdvertisingParser.parseAdvertisingData(rawBytes)

            // Create table rows for each data item
            for (item in dataItems) {
                val tableRow = TableRow(context)
                tableRow.setPadding(2, 2, 2, 2)

                // Length column
                val lengthTextView = TextView(context)
                lengthTextView.text = item.length.toString()
                lengthTextView.setPadding(3, 3, 3, 3)
                tableRow.addView(lengthTextView)

                // Type column
                val typeTextView = TextView(context)
                typeTextView.text = "0x${String.format("%02X", item.typeCode)}"
                typeTextView.setPadding(3, 3, 3, 3)
                tableRow.addView(typeTextView)

                // Value column
                val valueTextView = TextView(context)
                val valueText = StringBuilder()
                valueText.append(item.value)
                valueText.append("\n")
                valueText.append(item.type)

                // Add UTF-8 text interpretation if available
                if (!item.textValue.isNullOrEmpty()) {
                    valueText.append("\n")

                    val builder = SpannableStringBuilder()
                    builder.append("Text (UTF-8): ".bold())
                    builder.append(item.textValue)

                    valueTextView.text = valueText.toString()
                    valueTextView.append(builder)
                } else {
                    valueTextView.text = valueText.toString()
                }
                valueTextView.setPadding(3, 3, 3, 3)
                tableRow.addView(valueTextView)

                tableLayout.addView(tableRow)
            }

        } catch (e: Exception) {
            // In case of parsing error, add an error row
            val errorRow = TableRow(context)
            val errorTextView = TextView(context)
            errorTextView.text = "Error parsing data: ${e.message}"
            errorTextView.setPadding(3, 3, 3, 3)
            errorRow.addView(errorTextView)
            tableLayout.addView(errorRow)
        }
    }
}