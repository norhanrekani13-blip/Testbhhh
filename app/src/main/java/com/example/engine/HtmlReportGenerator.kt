package com.example.engine

import com.example.model.ApkFileEntry
import com.example.model.DexAnalysisResult
import com.example.model.FileCategory
import com.example.model.TechDetection
import java.util.zip.ZipFile

/**
 * Generates a self-contained, interactive HTML5 Code & Architecture Report
 * containing detected languages/frameworks, decoded AndroidManifest.xml,
 * DEX package & class outlines, file tree, and embedded HTML/XML/JS/CSS/text source files.
 */
object HtmlReportGenerator {

    fun generateHtmlReport(
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Long,
        minSdk: Int,
        targetSdk: Int,
        apkSizeBytes: Long,
        techDetections: List<TechDetection>,
        decodedManifestXml: String,
        permissions: List<String>,
        activities: List<String>,
        nativeAbis: List<String>,
        entries: List<ApkFileEntry>,
        dexAnalysis: DexAnalysisResult,
        zipFile: ZipFile?
    ): String {
        val extractedTextSnippets = mutableListOf<Pair<String, String>>()

        if (zipFile != null) {
            val readableEntries = entries.filter {
                (it.category == FileCategory.WEB_HTML ||
                    (it.category == FileCategory.XML_MANIFEST && it.path != "AndroidManifest.xml") ||
                    it.extension in TEXT_EXTENSIONS) &&
                    it.sizeBytes in 1..250_000
            }.take(150)

            for (entry in readableEntries) {
                try {
                    val zipEntry = zipFile.getEntry(entry.path) ?: continue
                    val rawBytes = zipFile.getInputStream(zipEntry).use { it.readBytes() }
                    val decoded = if (BinaryXmlDecoder.isBinaryXml(rawBytes)) {
                        BinaryXmlDecoder.decode(rawBytes)
                    } else {
                        String(rawBytes, Charsets.UTF_8)
                    }
                    extractedTextSnippets.add(entry.path to decoded.take(60_000))
                } catch (_: Exception) {
                }
            }
        }

        val sb = StringBuilder(32_768)
        sb.append(
            """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${esc(appName)} — CodeLens HTML Report</title>
<style>
  :root {
    --bg: #0b101b;
    --surface: #121927;
    --surface2: #1a2438;
    --border: #2b3954;
    --text: #e2e8f0;
    --muted: #94a3b8;
    --cyan: #22d3ee;
    --emerald: #34d399;
    --amber: #fbbf24;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 20px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.55;
  }
  .container { max-width: 1100px; margin: 0 auto; }
  .hero {
    background: linear-gradient(135deg, #0f172a, #112236);
    border: 1px solid var(--border);
    border-radius: 18px;
    padding: 24px;
    margin-bottom: 20px;
  }
  .hero h1 { margin: 0 0 6px; font-size: 26px; color: #fff; }
  .pkg { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: var(--cyan); font-size: 14px; }
  .meta-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 12px;
    margin-top: 16px;
  }
  .meta-card {
    background: var(--surface2);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 12px;
  }
  .meta-label { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: .06em; }
  .meta-value { font-size: 15px; font-weight: 600; margin-top: 4px; }
  .section {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 20px;
    margin-bottom: 20px;
  }
  .section h2 { margin: 0 0 14px; font-size: 18px; color: var(--cyan); }
  .tech-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
  .tech-card {
    background: var(--surface2);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 14px;
  }
  .tech-head { display: flex; justify-content: space-between; align-items: center; font-weight: 700; }
  .badge {
    background: rgba(34, 211, 238, 0.14);
    color: var(--cyan);
    padding: 2px 9px;
    border-radius: 999px;
    font-size: 12px;
  }
  .bar { height: 6px; background: #0f172a; border-radius: 999px; margin: 10px 0; overflow: hidden; }
  .bar > span { display: block; height: 100%; background: linear-gradient(90deg, var(--cyan), var(--emerald)); }
  .evidence { font-size: 12px; color: var(--muted); font-family: monospace; }
  pre {
    background: #070b13;
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 14px;
    overflow-x: auto;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12.5px;
    color: #cbd5e1;
    max-height: 460px;
  }
  .chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
  .chip {
    background: var(--surface2);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 4px 10px;
    font-size: 12px;
    font-family: monospace;
  }
  input.search {
    width: 100%;
    padding: 10px 14px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: var(--bg);
    color: var(--text);
    margin-bottom: 12px;
    font-size: 14px;
  }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); font-family: monospace; }
  th { color: var(--muted); font-family: sans-serif; font-size: 12px; }
  details {
    background: var(--surface2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 14px;
    margin-bottom: 10px;
  }
  summary { cursor: pointer; font-weight: 600; font-family: monospace; color: var(--emerald); }
</style>
</head>
<body>
<div class="container">
  <div class="hero">
    <h1>${esc(appName)}</h1>
    <div class="pkg">${esc(packageName)}</div>
    <div class="meta-grid">
      <div class="meta-card"><div class="meta-label">Version</div><div class="meta-value">${esc(versionName)} ($versionCode)</div></div>
      <div class="meta-card"><div class="meta-label">SDK (Min / Target)</div><div class="meta-value">API $minSdk / API $targetSdk</div></div>
      <div class="meta-card"><div class="meta-label">APK Size</div><div class="meta-value">${formatBytes(apkSizeBytes)}</div></div>
      <div class="meta-card"><div class="meta-label">Total Entries</div><div class="meta-value">${entries.size} files</div></div>
      <div class="meta-card"><div class="meta-label">DEX Classes</div><div class="meta-value">${dexAnalysis.totalClasses} classes</div></div>
      <div class="meta-card"><div class="meta-label">DEX Methods</div><div class="meta-value">${dexAnalysis.totalMethods} methods</div></div>
    </div>
  </div>

  <div class="section">
    <h2>Detected Languages &amp; Frameworks</h2>
    <div class="tech-list">
            """.trimIndent()
        )

        for (tech in techDetections) {
            sb.append(
                """
      <div class="tech-card">
        <div class="tech-head">
          <span>${esc(tech.name)}</span>
          <span class="badge">${tech.confidencePercent}%</span>
        </div>
        <div style="font-size:13px;color:var(--muted);margin-top:4px;">${esc(tech.subtitleEn)}</div>
        <div class="bar"><span style="width:${tech.confidencePercent}%"></span></div>
        <div class="evidence">${tech.evidence.joinToString("<br/>") { esc(it) }}</div>
      </div>
                """.trimIndent()
            )
        }
        sb.append("</div></div>")

        if (nativeAbis.isNotEmpty() || permissions.isNotEmpty()) {
            sb.append("<div class=\"section\"><h2>Permissions &amp; Native Architectures</h2>")
            if (nativeAbis.isNotEmpty()) {
                sb.append("<p><strong>Native ABIs:</strong></p><div class=\"chip-row\">")
                nativeAbis.forEach { sb.append("<span class=\"chip\">${esc(it)}</span>") }
                sb.append("</div><br/>")
            }
            if (permissions.isNotEmpty()) {
                sb.append("<p><strong>Permissions (${permissions.size}):</strong></p><div class=\"chip-row\">")
                permissions.forEach { sb.append("<span class=\"chip\">${esc(it)}</span>") }
                sb.append("</div>")
            }
            sb.append("</div>")
        }

        if (activities.isNotEmpty()) {
            sb.append("<div class=\"section\"><h2>Declared Activities (${activities.size})</h2><div class=\"chip-row\">")
            activities.take(40).forEach { sb.append("<span class=\"chip\">${esc(it)}</span>") }
            sb.append("</div></div>")
        }

        sb.append(
            """
  <div class="section">
    <h2>Decoded AndroidManifest.xml</h2>
    <pre><code>${esc(decodedManifestXml)}</code></pre>
  </div>

  <div class="section">
    <h2>DEX Packages &amp; Extracted Class Outline</h2>
    <pre><code>${esc(dexAnalysis.sampleClassOutline)}</code></pre>
  </div>
            """.trimIndent()
        )

        if (extractedTextSnippets.isNotEmpty()) {
            sb.append("<div class=\"section\"><h2>Extracted HTML, XML, JS, CSS &amp; Text Source Files (${extractedTextSnippets.size})</h2>")
            for ((path, code) in extractedTextSnippets) {
                sb.append(
                    """
    <details>
      <summary>${esc(path)}</summary>
      <pre><code>${esc(code)}</code></pre>
    </details>
                    """.trimIndent()
                )
            }
            sb.append("</div>")
        }

        sb.append(
            """
  <div class="section">
    <h2>Complete APK File Tree (${entries.size})</h2>
    <input class="search" type="text" placeholder="Filter files by name or path..." oninput="filterFiles(this.value)" />
    <table id="filesTable">
      <thead><tr><th>Path</th><th>Category</th><th>Size</th></tr></thead>
      <tbody>
            """.trimIndent()
        )

        for (entry in entries.take(600)) {
            sb.append(
                "<tr><td>${esc(entry.path)}</td><td>${entry.category.name}</td><td>${formatBytes(entry.sizeBytes)}</td></tr>\n"
            )
        }
        if (entries.size > 600) {
            sb.append("<tr><td colspan=\"3\">... and ${entries.size - 600} more files (included in full ZIP bundle)</td></tr>")
        }

        sb.append(
            """
      </tbody>
    </table>
  </div>
</div>
<script>
function filterFiles(q) {
  var term = q.toLowerCase();
  var rows = document.querySelectorAll('#filesTable tbody tr');
  rows.forEach(function(r) {
    r.style.display = r.textContent.toLowerCase().indexOf(term) !== -1 ? '' : 'none';
  });
}
</script>
</body>
</html>
            """.trimIndent()
        )
        return sb.toString()
    }

    private fun esc(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }

    private val TEXT_EXTENSIONS = setOf(
        "html", "htm", "xhtml", "css", "js", "mjs", "ts", "json", "xml",
        "txt", "md", "properties", "pro", "cfg", "ini", "yaml", "yml",
        "svg", "glsl", "frag", "vert", "csv", "sql", "gradle"
    )
}
