package com.stpl.documents

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val blue = Color.rgb(61, 114, 157)
    private val dark = Color.rgb(35, 35, 35)
    private val lightBlue = Color.rgb(235, 242, 248)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHomeScreen()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(blue)
        }

        val company = TextView(this).apply {
            text = "SANJAY TECHNOMECH PVT LTD"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "STPL DOCUMENT GENERATOR"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        header.addView(company)
        header.addView(subtitle)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(125)
            )
        )

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(30))
        }

        val title = TextView(this).apply {
            text = "Create Document"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(dark)
        }

        val description = TextView(this).apply {
            text = "Select the document you want to prepare."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(24))
        }

        content.addView(title)
        content.addView(description)

        content.addView(
            documentButton(
                "TAX INVOICE",
                "GST invoice with CGST + SGST or IGST"
            ) {
                openActivity("TaxInvoiceActivity")
            }
        )

        content.addView(
            documentButton(
                "QUOTATION",
                "Prepare an STPL quotation / estimate"
            ) {
                openActivity("QuotationActivity")
            }
        )

        content.addView(
            documentButton(
                "DELIVERY CHALLAN",
                "Prepare an STPL delivery challan"
            ) {
                openActivity("DeliveryChallanActivity")
            }
        )

        val footer = TextView(this).apply {
            text = "Sanjay Technomech Pvt Ltd\nGhaziabad, Uttar Pradesh"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(35), 0, 0)
        }

        content.addView(footer)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun documentButton(
        title: String,
        subtitle: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = "$title\n$subtitle"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            setBackgroundColor(lightBlue)
            gravity = Gravity.CENTER
            isAllCaps = false
            setPadding(dp(12), dp(14), dp(12), dp(14))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(78)
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params

            setOnClickListener { action() }
        }
    }

    private fun openActivity(simpleClassName: String) {
        try {
            val fullClassName = "$packageName.$simpleClassName"
            val target = Class.forName(fullClassName)
            startActivity(Intent(this, target))
        } catch (e: ClassNotFoundException) {
            Toast.makeText(
                this,
                "$simpleClassName is not added yet.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not open $simpleClassName.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
