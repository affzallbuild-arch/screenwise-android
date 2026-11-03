package com.screenwise.app.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.screenwise.app.usage.UsageSnapshot
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReportExporter {
    private fun csvCell(raw: String): String {
        // Protect spreadsheet users from accidental formula execution.
        val value = if (raw.startsWith('=') || raw.startsWith('+') || raw.startsWith('-') || raw.startsWith('@')) "'${raw}" else raw
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    fun writeCsv(context: Context, uri: Uri, data: UsageSnapshot) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write("Package,App,Category,Minutes,Sessions\r\n".toByteArray())
            data.apps.forEach { app ->
                val line = listOf(csvCell(app.packageName), csvCell(app.label), csvCell(app.category),
                    (app.durationMs / 60000.0).toString(), app.sessionCount?.toString() ?: "unavailable")
                    .joinToString(",") + "\r\n"
                stream.write(line.toByteArray(Charsets.UTF_8))
            }
        } ?: error("Unable to open destination")
    }
    fun writePdf(context: Context, uri: Uri, data: UsageSnapshot) {
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault())
        var pageNum = 0
        var y = 0f
        var page: PdfDocument.Page? = null
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            y = 54f
        }
        fun line(value: String, head: Boolean = false) {
            if (page == null || y > 790f) newPage()
            page!!.canvas.drawText(value.take(85), 42f, y, if (head) title else normal)
            y += if (head) 38f else 22f
        }
        try {
            line("ScreenWise usage report", true)
            line("Period: ${fmt.format(Instant.ofEpochMilli(data.from))} - ${fmt.format(Instant.ofEpochMilli(data.to))}")
            line("Total app foreground time: ${data.totalMs / 60000} minutes")
            line("History is OS-supplied; availability and accuracy may vary.")
            line(" ")
            data.apps.forEach { app ->
                line("${app.label.take(40)} | ${app.durationMs / 60000} min | ${app.category}")
            }
            if (data.apps.isEmpty()) line("No usage data provided for this period.")
            page?.let { doc.finishPage(it) }
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("Unable to open destination")
        } finally { doc.close() }
    }
}
