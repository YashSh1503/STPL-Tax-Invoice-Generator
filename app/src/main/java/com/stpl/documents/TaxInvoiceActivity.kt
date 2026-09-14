package com.stpl.documents

import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToLong

class TaxInvoiceActivity : AppCompatActivity() {

    private lateinit var itemsContainer: LinearLayout
    private lateinit var tvSubtotal: TextView
    private lateinit var tvDiscount: TextView
    private lateinit var tvTaxable: TextView
    private lateinit var tvCgst: TextView
    private lateinit var tvSgst: TextView
    private lateinit var tvIgst: TextView
    private lateinit var tvGrandTotal: TextView
    private lateinit var tvAmountWords: TextView
    private lateinit var taxRateEditText: EditText
    private lateinit var taxTypeSpinner: Spinner

    private var itemNumber = 0

    private data class ItemViews(
        val layout: LinearLayout,
        val name: EditText,
        val code: EditText,
        val hsn: EditText,
        val quantity: EditText,
        val unit: EditText,
        val price: EditText,
        val discount: EditText
    )

    private val items = mutableListOf<ItemViews>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContentView(R.layout.activity_tax_invoice)

        itemsContainer = findViewById(R.id.itemsContainer)
        taxRateEditText = findViewById(R.id.etTaxRate)
        taxTypeSpinner = findViewById(R.id.spTaxType)

        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvDiscount = findViewById(R.id.tvDiscount)
        tvTaxable = findViewById(R.id.tvTaxable)
        tvCgst = findViewById(R.id.tvCgst)
        tvSgst = findViewById(R.id.tvSgst)
        tvIgst = findViewById(R.id.tvIgst)
        tvGrandTotal = findViewById(R.id.tvGrandTotal)
        tvAmountWords = findViewById(R.id.tvAmountWords)

        val paymentSpinner = findViewById<Spinner>(R.id.spPaymentMode)
        val generateButton = findViewById<Button>(R.id.btnGenerateInvoice)
        val addItemButton = findViewById<Button>(R.id.btnAddItem)

        paymentSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf(
                "Select Payment Mode",
                "Cash",
                "UPI",
                "Bank Transfer",
                "Cheque",
                "Credit",
                "Other"
            )
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        taxTypeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Select Tax Type", "CGST + SGST", "IGST")
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        addItem()

        addItemButton.setOnClickListener {
            if (items.size < 15) {
                addItem()
            } else {
                Toast.makeText(
                    this,
                    "Maximum 15 items allowed on this one-page invoice.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        taxRateEditText.addTextChangedListener(simpleWatcher { calculateTotals() })

        taxTypeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) = calculateTotals()

                override fun onNothingSelected(parent: AdapterView<*>?) = calculateTotals()
            }

        generateButton.setOnClickListener {
            calculateTotals()
            generateInvoicePdf()
        }
    }

    private fun simpleWatcher(action: () -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                action()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

    private fun addItem() {
        itemNumber++

        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
        }

        val title = TextView(this).apply {
            text = "ITEM $itemNumber"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(8, 126, 164))
            setPadding(12, 12, 12, 12)
        }
        itemLayout.addView(title)

        val name = createTextField("Item Name", false)
        val code = createTextField("Item Code (Optional)", false)
        val hsn = createTextField("HSN / SAC", false)
        val quantity = createTextField("Quantity", true)
        val unit = createTextField("Unit", false)
        val price = createTextField("Price / Unit", true)
        val discount = createTextField("Discount % (Optional)", true)

        itemLayout.addView(name)
        itemLayout.addView(code)
        itemLayout.addView(hsn)
        itemLayout.addView(quantity)
        itemLayout.addView(unit)
        itemLayout.addView(price)
        itemLayout.addView(discount)

        val removeButton = Button(this).apply {
            text = "REMOVE ITEM"
            setOnClickListener {
                itemsContainer.removeView(itemLayout)
                items.removeAll { it.layout == itemLayout }
                calculateTotals()
            }
        }
        itemLayout.addView(removeButton)

        itemsContainer.addView(itemLayout)

        items.add(
            ItemViews(
                layout = itemLayout,
                name = name,
                code = code,
                hsn = hsn,
                quantity = quantity,
                unit = unit,
                price = price,
                discount = discount
            )
        )

        addCalculationWatcher(quantity)
        addCalculationWatcher(price)
        addCalculationWatcher(discount)
        calculateTotals()
    }

    private fun createTextField(hintText: String, numeric: Boolean): EditText =
        EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = hintText
            textSize = 15f
            inputType =
                if (numeric) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
        }

    private fun addCalculationWatcher(editText: EditText) {
        editText.addTextChangedListener(simpleWatcher { calculateTotals() })
    }

    private data class Totals(
        val subtotal: Double,
        val discount: Double,
        val taxable: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val total: Double,
        val rate: Double
    )

    private fun getTotals(): Totals {
        var subtotal = 0.0
        var discountTotal = 0.0

        items.forEach { item ->
            val qty = item.quantity.text.toString().toDoubleOrNull() ?: 0.0
            val price = item.price.text.toString().toDoubleOrNull() ?: 0.0
            val discountPercent = item.discount.text.toString().toDoubleOrNull() ?: 0.0

            val gross = qty * price
            subtotal += gross
            discountTotal += gross * discountPercent / 100.0
        }

        val taxable = (subtotal - discountTotal).coerceAtLeast(0.0)
        val rate = taxRateEditText.text.toString().toDoubleOrNull() ?: 0.0

        var cgst = 0.0
        var sgst = 0.0
        var igst = 0.0

        when (taxTypeSpinner.selectedItemPosition) {
            1 -> {
                cgst = taxable * (rate / 2.0) / 100.0
                sgst = taxable * (rate / 2.0) / 100.0
            }
            2 -> igst = taxable * rate / 100.0
        }

        return Totals(
            subtotal,
            discountTotal,
            taxable,
            cgst,
            sgst,
            igst,
            taxable + cgst + sgst + igst,
            rate
        )
    }

    private fun calculateTotals() {
        val t = getTotals()

        tvSubtotal.text = "Subtotal: ${formatMoney(t.subtotal)}"
        tvDiscount.text = "Discount: ${formatMoney(t.discount)}"
        tvTaxable.text = "Taxable Amount: ${formatMoney(t.taxable)}"
        tvCgst.text = "CGST: ${formatMoney(t.cgst)}"
        tvSgst.text = "SGST: ${formatMoney(t.sgst)}"
        tvIgst.text = "IGST: ${formatMoney(t.igst)}"
        tvGrandTotal.text = "GRAND TOTAL: ${formatMoney(t.total)}"
        tvAmountWords.text = "Amount in Words: ${amountInWords(t.total)}"
    }

    // -------------------------------------------------------------------------
    // PDF GENERATION
    //
    // IMPORTANT:
    // The STPL PDF in assets is the master artwork. We do NOT redraw the invoice.
    // This preserves the original STPL logo, signature, borders, column widths,
    // blue headers and spacing. PDFBox only overlays the editable values.
    // -------------------------------------------------------------------------

    private fun generateInvoicePdf() {
        try {
            val totals = getTotals()

            val templateName =
                if (taxTypeSpinner.selectedItemPosition == 2) {
                    "STPL_IGST_TEMPLATE.pdf"
                } else {
                    "STPL_CGST_SGST_TEMPLATE.pdf"
                }

            val doc = PDDocument.load(assets.open(templateName))
            val page = doc.getPage(0)

            // The supplied STPL artwork is 612 x 792 points (Letter).
            // We deliberately keep the template's native page size.
            val output = ByteArrayOutputStream()

            PDPageContentStream(
                doc,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { cs ->
                drawTemplateData(cs, page, totals)
            }

            doc.save(output)
            doc.close()

            val fileName =
                "STPL_Tax_Invoice_${System.currentTimeMillis()}.pdf"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )

            if (uri == null) {
                Toast.makeText(
                    this,
                    "Could not create PDF file.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            contentResolver.openOutputStream(uri).use { stream ->
                if (stream == null) {
                    throw IllegalStateException("Could not open Downloads output stream.")
                }
                stream.write(output.toByteArray())
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)

            Toast.makeText(
                this,
                "Tax Invoice PDF saved to Downloads.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDF error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun drawTemplateData(
        cs: PDPageContentStream,
        page: PDPage,
        totals: Totals
    ) {
        val isIgst = taxTypeSpinner.selectedItemPosition == 2

        // ---------------- HEADER / CUSTOMER ----------------

        putText(
            cs,
            505f,
            681f,
            firstField(
                "po number",
                "purchase order number",
                "purchase order",
                "po"
            ),
            7.2f,
            align = TextAlign.RIGHT
        )

        val customerName = firstField(
            "company name",
            "customer name",
            "party name",
            "customer",
            "bill to",
            "buyer"
        )

        val customerAddress = firstField(
            "billing address",
            "bill to address",
            "customer address",
            "party address",
            "address"
        )

        val customerGstin = firstField(
            "customer gstin",
            "party gstin",
            "gstin"
        )

        val customerState = firstField(
            "customer state",
            "state"
        )

        val shipTo = firstField(
            "ship to",
            "shipping address",
            "delivery address",
            "ship address",
            "destination"
        )

        val placeOfSupply = firstField(
            "place of supply",
            "supply"
        )

        val transport = firstField(
            "transport name",
            "transporter name",
            "transporter",
            "transport"
        )

        val vehicle = firstField(
            "vehicle number",
            "vehicle no",
            "vehicle"
        )

        val invoiceNo = firstField(
            "invoice number",
            "invoice no",
            "invoice"
        )

        val invoiceDate = firstField(
            "invoice date",
            "date"
        )

        val poDate = firstField(
            "po date",
            "purchase order date",
            "purchase date"
        )

        val poNo = firstField(
            "po number",
            "purchase order number",
            "purchase order",
            "po"
        )

        val eway = firstField(
            "e-way bill number",
            "e-way bill",
            "eway bill",
            "e-way",
            "eway"
        )

        // Bill To — these coordinates are inside the EXISTING template box.
        putWrapped(cs, 38f, 650f, customerName, 7.6f, 120f, 10f, 2, bold = true)
        putWrapped(cs, 38f, 628f, customerAddress, 7.1f, 120f, 9f, 4)
        putText(cs, 38f, 588f, "GSTIN : $customerGstin", 7.0f)
        putText(cs, 38f, 573f, "State: $customerState", 7.0f)

        // Ship To
        putWrapped(cs, 188f, 650f, shipTo, 7.1f, 135f, 9f, 6)

        // Transportation
        putText(cs, 332f, 650f, "Transport Name: $transport", 7.0f)
        putText(cs, 332f, 635f, "Vehicle Number: $vehicle", 7.0f)

        // Invoice Details
        putText(cs, 486f, 650f, "Invoice No. : $invoiceNo", 7.0f)
        putText(cs, 486f, 635f, "Date : $invoiceDate", 7.0f)
        putText(cs, 332f, 610f, "PO Date : $poDate", 7.0f)
        putText(cs, 332f, 595f, "PO Number : $poNo", 7.0f)
        putWrapped(cs, 486f, 610f, "E-way Bill number: $eway", 7.0f, 85f, 9f, 2)
        putWrapped(cs, 486f, 575f, "Place of supply: $placeOfSupply", 7.0f, 85f, 9f, 2)

        // ---------------- ITEM TABLE ----------------
        //
        // These coordinates ONLY place text into the cells that already exist
        // in the selected STPL template. No new columns or grid lines are drawn.

        val rowTop = 525f
        val rowStep = 23.0f
        val maxRows = 15

        items.take(maxRows).forEachIndexed { index, item ->
            val y = rowTop - index * rowStep

            val qty = item.quantity.text.toString().toDoubleOrNull() ?: 0.0
            val price = item.price.text.toString().toDoubleOrNull() ?: 0.0
            val discountPct = item.discount.text.toString().toDoubleOrNull() ?: 0.0
            val gross = qty * price
            val discountAmount = gross * discountPct / 100.0
            val taxableLine = gross - discountAmount

            putText(cs, 36f, y, "${index + 1}", 6.7f)

            if (isIgst) {
                // IGST template: # | Item | HSN/SAC | Item Code | Qty | Unit | Price | Amount
                putWrapped(cs, 53f, y, item.name.text.toString(), 6.6f, 145f, 8f, 4, bold = true)
                putText(cs, 205f, y, item.hsn.text.toString(), 6.6f)
                putWrapped(cs, 264f, y, item.code.text.toString(), 6.4f, 48f, 8f, 3)
                putTextRight(cs, 382f, y, cleanNumber(qty), 6.6f)
                putTextRight(cs, 440f, y, item.unit.text.toString(), 6.6f)
                putTextRight(cs, 507f, y, money(price), 6.6f)
                putTextRight(cs, 575f, y, money(gross), 6.6f)
            } else {
                // CGST/SGST template:
                // # | Item | HSN/SAC | Qty | Unit | Price | Discount |
                // Taxable amount | CGST | SGST | Amount
                putWrapped(cs, 53f, y, item.name.text.toString(), 6.5f, 95f, 8f, 4, bold = true)
                putText(cs, 151f, y, item.hsn.text.toString(), 6.4f)
                putTextRight(cs, 224f, y, cleanNumber(qty), 6.5f)
                putTextRight(cs, 267f, y, item.unit.text.toString(), 6.5f)
                putTextRight(cs, 326f, y, money(price), 6.5f)

                if (discountPct != 0.0) {
                    putWrapped(
                        cs,
                        331f,
                        y,
                        "${money(discountAmount)} (${cleanNumber(discountPct)}%)",
                        6.0f,
                        43f,
                        7f,
                        2
                    )
                }

                putTextRight(cs, 425f, y, money(taxableLine), 6.4f)

                val lineCgst = taxableLine * (totals.rate / 2.0) / 100.0
                val lineSgst = taxableLine * (totals.rate / 2.0) / 100.0

                putWrapped(
                    cs,
                    427f,
                    y,
                    "${money(lineCgst)} (${cleanNumber(totals.rate / 2.0)}%)",
                    5.9f,
                    45f,
                    7f,
                    2
                )
                putWrapped(
                    cs,
                    476f,
                    y,
                    "${money(lineSgst)} (${cleanNumber(totals.rate / 2.0)}%)",
                    5.9f,
                    45f,
                    7f,
                    2
                )
                putTextRight(cs, 575f, y, money(taxableLine + lineCgst + lineSgst), 6.4f)
            }
        }

        // ---------------- TOTAL ROW ----------------

        putText(cs, 54f, 295f, "Total", 7.0f, bold = true)

        if (isIgst) {
            val totalQty = items.sumOf {
                it.quantity.text.toString().toDoubleOrNull() ?: 0.0
            }
            putTextRight(cs, 382f, 295f, cleanNumber(totalQty), 7.0f, bold = true)
            putTextRight(cs, 575f, 295f, money(totals.taxable), 7.0f, bold = true)
        } else {
            val totalQty = items.sumOf {
                it.quantity.text.toString().toDoubleOrNull() ?: 0.0
            }
            putTextRight(cs, 224f, 295f, cleanNumber(totalQty), 7.0f, bold = true)
            putTextRight(cs, 326f, 295f, money(totals.subtotal), 7.0f, bold = true)
            putTextRight(cs, 425f, 295f, money(totals.taxable), 7.0f, bold = true)
            putTextRight(cs, 475f, 295f, money(totals.cgst), 7.0f, bold = true)
            putTextRight(cs, 524f, 295f, money(totals.sgst), 7.0f, bold = true)
            putTextRight(cs, 575f, 295f, money(totals.total), 7.0f, bold = true)
        }

        // ---------------- TAX / TOTALS ----------------

        if (isIgst) {
            putText(cs, 38f, 270f, "IGST", 7.0f)
            putTextRight(cs, 200f, 270f, money(totals.taxable), 7.0f)
            putTextRight(cs, 270f, 270f, "${cleanNumber(totals.rate)}%", 7.0f)
            putTextRight(cs, 345f, 270f, money(totals.igst), 7.0f)

            putText(cs, 395f, 270f, "Sub Total", 7.0f)
            putTextRight(cs, 575f, 270f, money(totals.taxable), 7.0f)

            putText(cs, 395f, 248f, "Tax (${cleanNumber(totals.rate)}%)", 7.0f)
            putTextRight(cs, 575f, 248f, money(totals.igst), 7.0f)

            putText(cs, 395f, 226f, "Total", 7.3f, bold = true)
            putTextRight(cs, 575f, 226f, money(totals.total), 7.3f, bold = true)

            putText(cs, 395f, 204f, "Received", 7.0f)
            putTextRight(cs, 575f, 204f, "0.00", 7.0f)

            putText(cs, 395f, 182f, "Balance", 7.0f)
            putTextRight(cs, 575f, 182f, money(totals.total), 7.0f)
        } else {
            putText(cs, 38f, 270f, "SGST", 7.0f)
            putTextRight(cs, 200f, 270f, money(totals.taxable), 7.0f)
            putTextRight(cs, 270f, 270f, "${cleanNumber(totals.rate / 2.0)}%", 7.0f)
            putTextRight(cs, 345f, 270f, money(totals.sgst), 7.0f)

            putText(cs, 38f, 249f, "CGST", 7.0f)
            putTextRight(cs, 200f, 249f, money(totals.taxable), 7.0f)
            putTextRight(cs, 270f, 249f, "${cleanNumber(totals.rate / 2.0)}%", 7.0f)
            putTextRight(cs, 345f, 249f, money(totals.cgst), 7.0f)

            putText(cs, 395f, 270f, "Sub Total", 7.0f)
            putTextRight(cs, 575f, 270f, money(totals.taxable), 7.0f)

            putText(cs, 395f, 249f, "Tax (${cleanNumber(totals.rate)}%)", 7.0f)
            putTextRight(cs, 575f, 249f, money(totals.cgst + totals.sgst), 7.0f)

            putText(cs, 395f, 228f, "Total", 7.3f, bold = true)
            putTextRight(cs, 575f, 228f, money(totals.total), 7.3f, bold = true)

            putText(cs, 395f, 207f, "Received", 7.0f)
            putTextRight(cs, 575f, 207f, "0.00", 7.0f)

            putText(cs, 395f, 186f, "Balance", 7.0f)
            putTextRight(cs, 575f, 186f, money(totals.total), 7.0f)

            putText(cs, 395f, 165f, "You Saved", 7.0f)
            putTextRight(cs, 575f, 165f, money(totals.discount), 7.0f)
        }

        // ---------------- AMOUNT IN WORDS / PAYMENT ----------------

        putWrapped(
            cs,
            38f,
            155f,
            amountInWords(totals.total),
            7.0f,
            275f,
            9f,
            2
        )

        putText(
            cs,
            38f,
            132f,
            paymentMode(),
            7.2f
        )

        // The template already contains the original terms, company name and
        // authorized-signatory artwork. We intentionally do not redraw them.
    }

    private enum class TextAlign { LEFT, RIGHT, CENTER }

    private fun putText(
        cs: PDPageContentStream,
        x: Float,
        y: Float,
        value: String,
        size: Float,
        bold: Boolean = false,
        align: TextAlign = TextAlign.LEFT
    ) {
        val clean = safePdfText(value)
        if (clean.isBlank()) return

        val font = if (bold) {
            PDType1Font.HELVETICA_BOLD
        } else {
            PDType1Font.HELVETICA
        }

        val width = font.getStringWidth(clean) / 1000f * size
        val drawX = when (align) {
            TextAlign.LEFT -> x
            TextAlign.RIGHT -> x - width
            TextAlign.CENTER -> x - width / 2f
        }

        cs.beginText()
        cs.setFont(font, size)
        cs.setNonStrokingColor(35, 35, 35)
        cs.newLineAtOffset(drawX, y)
        cs.showText(clean)
        cs.endText()
    }

    private fun putTextRight(
        cs: PDPageContentStream,
        rightX: Float,
        y: Float,
        value: String,
        size: Float,
        bold: Boolean = false
    ) {
        putText(cs, rightX, y, value, size, bold, TextAlign.RIGHT)
    }

    private fun putWrapped(
        cs: PDPageContentStream,
        x: Float,
        y: Float,
        value: String,
        size: Float,
        maxWidth: Float,
        lineStep: Float,
        maxLines: Int,
        bold: Boolean = false
    ) {
        val clean = safePdfText(value)
        if (clean.isBlank()) return

        val font = if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        val words = clean.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""

        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            val width = font.getStringWidth(candidate) / 1000f * size

            if (width <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines.add(current)
                current = word
                if (lines.size == maxLines) break
            }
        }

        if (lines.size < maxLines && current.isNotBlank()) {
            lines.add(current)
        }

        lines.take(maxLines).forEachIndexed { index, line ->
            putText(cs, x, y - index * lineStep, line, size, bold)
        }
    }

    // Finds a field by its hint or Android resource ID.
    // More specific phrases are checked first by the caller.
    private fun firstField(vararg keywords: String): String {
        val root = window.decorView
        val all = mutableListOf<EditText>()
        collectEditTexts(root, all, itemsContainer)

        val wanted = keywords.map { it.lowercase(Locale.US) }

        for (keyword in wanted) {
            for (view in all) {
                val hint = view.hint?.toString()?.lowercase(Locale.US) ?: ""
                val idName =
                    if (view.id != View.NO_ID) {
                        try {
                            resources.getResourceEntryName(view.id)
                                .lowercase(Locale.US)
                        } catch (_: Exception) {
                            ""
                        }
                    } else {
                        ""
                    }

                val combined = "$hint $idName"

                if (combined.contains(keyword)) {
                    val value = view.text.toString().trim()
                    if (value.isNotBlank()) return value
                }
            }
        }

        return ""
    }

    // -------------------------------------------------------------------------
    // Generic field lookup
    //
    // We do not require new XML IDs. The renderer searches the existing form for
    // EditTexts by their hint/id text. If a field is not present in the current
    // XML, it simply renders blank instead of breaking compilation.
    // -------------------------------------------------------------------------

    private fun fieldValue(vararg keywords: String): String {
        val root = window.decorView
        val all = mutableListOf<EditText>()
        collectEditTexts(root, all, itemsContainer)

        val wanted = keywords.map { it.lowercase(Locale.US) }

        for (view in all) {
            val hint = view.hint?.toString()?.lowercase(Locale.US) ?: ""
            val idName =
                if (view.id != View.NO_ID) {
                    try {
                        resources.getResourceEntryName(view.id)
                            .lowercase(Locale.US)
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }

            val combined = "$hint $idName"

            if (wanted.any { combined.contains(it) }) {
                return view.text.toString().trim()
            }
        }

        return ""
    }

    private fun collectEditTexts(
        view: View,
        result: MutableList<EditText>,
        excludedRoot: View
    ) {
        if (view === excludedRoot || view.isDescendantOf(excludedRoot)) return

        if (view is EditText) {
            result.add(view)
            return
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectEditTexts(view.getChildAt(i), result, excludedRoot)
            }
        }
    }

    private fun View.isDescendantOf(parent: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }

    private fun paymentMode(): String {
        val spinner = findViewById<Spinner>(R.id.spPaymentMode)
        return if (spinner.selectedItemPosition > 0) {
            spinner.selectedItem.toString()
        } else {
            ""
        }
    }


    private fun safePdfText(value: String): String =
        value
            .replace("₹", "Rs.")
            .replace("–", "-")
            .replace("—", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .filter { it.code in 32..126 }

    private fun cleanNumber(value: Double): String =
        if (value == value.roundToLong().toDouble()) {
            value.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    private fun money(value: Double): String =
        String.format(Locale.US, "%,.2f", value)

    private fun formatMoney(value: Double): String =
        String.format(Locale.US, "Rs.%,.2f", value)

    // -------------------------------------------------------------------------
    // Indian amount in words
    // -------------------------------------------------------------------------

    private fun amountInWords(amount: Double): String {
        val rounded = amount.roundToLong()
        if (rounded == 0L) return "Rupees Zero Only"
        return "Rupees ${convertIndianNumber(rounded)} Only"
    }

    private fun convertIndianNumber(number: Long): String {
        if (number == 0L) return "Zero"

        var n = number
        val parts = mutableListOf<String>()

        if (n >= 1_00_00_000) {
            val crore = n / 1_00_00_000
            parts.add("${convertIndianNumber(crore)} Crore")
            n %= 1_00_00_000
        }

        if (n >= 1_00_000) {
            val lakh = n / 1_00_000
            parts.add("${convertIndianNumber(lakh)} Lakh")
            n %= 1_00_000
        }

        if (n >= 1_000) {
            val thousand = n / 1_000
            parts.add("${convertIndianNumber(thousand)} Thousand")
            n %= 1_000
        }

        if (n >= 100) {
            val hundred = n / 100
            parts.add("${convertIndianNumber(hundred)} Hundred")
            n %= 100
        }

        if (n > 0) parts.add(convertTwoDigitNumber(n))

        return parts.joinToString(" ")
    }

    private fun convertTwoDigitNumber(number: Long): String {
        val ones = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
            "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
            "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
            "Nineteen"
        )

        val tens = arrayOf(
            "", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"
        )

        return when {
            number < 20 -> ones[number.toInt()]
            number % 10L == 0L -> tens[(number / 10).toInt()]
            else -> "${tens[(number / 10).toInt()]} ${ones[(number % 10).toInt()]}"
        }
    }
}

