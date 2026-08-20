package com.example.minicex

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.minicex.databinding.ActivityXlsViewerBinding
import com.example.minicex.data.remote.dto.*
import com.example.minicex.data.remote.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class XlsViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityXlsViewerBinding
    private var dataType: String = "teacher_summary"
    private var teacherSummary: TeacherSummaryResponse? = null
    private var studentReport: StudentReportResponse? = null
    private var evaluadorId: Int = -1
    private var modo: String = "mine"
    private var studentId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Force landscape + immersive full-screen ──────────────────────
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        binding = ActivityXlsViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Parse data
        dataType = intent.getStringExtra("data_type") ?: "teacher_summary"
        evaluadorId = intent.getIntExtra("evaluador_id", -1)
        modo = intent.getStringExtra("modo") ?: "mine"
        studentId = intent.getIntExtra("student_id", -1)

        when (dataType) {
            "student_report" -> {
                val json = intent.getStringExtra("student_report_json") ?: "{}"
                studentReport = Gson().fromJson(json, StudentReportResponse::class.java)
                binding.toolbarViewer.title = studentReport?.student?.nombreCompleto ?: "Reporte Alumno"
                binding.toolbarViewer.subtitle = "MAT. ${studentReport?.student?.matricula ?: ""}"
            }
            else -> {
                val json = intent.getStringExtra("teacher_summary_json") ?: "{}"
                teacherSummary = Gson().fromJson(json, TeacherSummaryResponse::class.java)
                binding.toolbarViewer.title = teacherSummary?.docente?.nombreCompleto ?: "Reporte Docente"
                binding.toolbarViewer.subtitle = "${teacherSummary?.alumnos?.size ?: 0} alumnos · ${
                    if (modo == "mine") "Mis evaluaciones" else "Todos los alumnos"
                }"
            }
        }
        binding.toolbarViewer.setNavigationOnClickListener { finish() }

        // Load HTML table into WebView
        binding.webViewXls.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            textZoom = 80
        }
        binding.webViewXls.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.tvRotateHint.visibility = View.GONE
            }
        }
        val html = when (dataType) {
            "student_report" -> generateStudentReportHtml(studentReport)
            else -> generateTeacherSummaryHtml(teacherSummary)
        }
        binding.webViewXls.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        binding.btnDownloadXls.setOnClickListener { downloadXlsxDirect() }
        binding.btnCloseViewer.setOnClickListener { finish() }
    }

    // ── Teacher Summary HTML ──────────────────────────────────────────────────

    private fun generateTeacherSummaryHtml(summary: TeacherSummaryResponse?): String {
        val sb = StringBuilder()
        sb.append(htmlHeader())
        val resumen = summary?.resumen
        val alumnos = summary?.alumnos ?: emptyList()

        sb.append(tabsHtml("Resumen Docente", "Detalle Alumnos (${alumnos.size})", "Evaluaciones", "Rúbricas"))

        // Tab 1: Resumen Docente
        sb.append("""<div id="tab-resumen" class="tab-content active">""")
        if (resumen != null) {
            sb.append("""
<div class="stats-grid">
  <div class="stat-card"><div class="label">Alumnos</div><div class="value" style="color:#818cf8">${resumen.totalAlumnos}</div></div>
  <div class="stat-card"><div class="label">Con evaluaciones</div><div class="value" style="color:#34d399">${resumen.alumnosConEvaluaciones}</div></div>
  <div class="stat-card"><div class="label">Total evaluaciones</div><div class="value" style="color:#fbbf24">${resumen.totalEvaluaciones}</div></div>
  <div class="stat-card"><div class="label">Promedio general</div><div class="value" style="color:${scoreColor(resumen.promedioGeneral)}">${"%.1f".format(resumen.promedioGeneral)}/10</div></div>
</div>
""".trimIndent())
        }
        sb.append("<table><tr><th>#</th><th>Alumno</th><th>Matrícula</th><th>Grupo</th><th>Evaluaciones</th><th>Promedio</th><th>Evolución</th><th>Cambio desde el inicio</th><th>Regularidad</th><th>Principal fortaleza</th><th>Área prioritaria</th></tr>")
        alumnos.forEachIndexed { i, a ->
            val idx = a.indices
            sb.append("""
<tr><td class="num">${i+1}</td><td>${escHtml(a.nombreCompleto)}</td><td>${escHtml(a.matricula)}</td><td>${escHtml(a.semestreGrupo)}</td><td class="num">${idx.totalEvaluaciones}</td><td class="num" style="color:${scoreColor(idx.promedioDisplay)}">${"%.1f".format(idx.promedioDisplay)}/10</td><td style="color:${trendColor(idx.trend)}">${escHtml(idx.trendText ?: "—")}</td><td class="num" style="color:${progressColor(idx.progreso)}">${if (idx.progreso>0)"+" else ""}${"%.1f".format(idx.progreso)}</td><td>${escHtml(idx.consistenciaText ?: "Sin datos")}</td><td style="color:#34d399">${escHtml(idx.competenciaFuerte ?: "—")}</td><td style="color:#f87171">${escHtml(idx.competenciaDebil ?: "—")}</td></tr>""".trimIndent())
        }
        sb.append("</table></div>")

        // Tab 2: Detalle Alumnos
        sb.append("""<div id="tab-alumnos" class="tab-content">""")
        alumnos.forEach { a ->
            val idx = a.indices
            val comps = a.competencias ?: emptyList()
            val evals = a.evaluaciones ?: emptyList()
            val areas = idx.topAreasMejora ?: emptyMap()
            sb.append("<h3>${escHtml(a.nombreCompleto)} · ${escHtml(a.matricula)}</h3>")
            sb.append("<div style=\"display:flex;gap:12px;flex-wrap:wrap;margin-bottom:8px\"><span style=\"color:${scoreColor(idx.promedioDisplay)};font-weight:700\">${"%.1f".format(idx.promedioDisplay)}/10</span><span style=\"color:#64748b\">${idx.totalEvaluaciones} evaluaciones</span><span style=\"color:${trendColor(idx.trend)}\">Evolución: ${escHtml(idx.trendText ?: "—")}</span><span style=\"color:#64748b\">Regularidad: ${escHtml(idx.consistenciaText ?: "Sin datos")}</span></div>")
            sb.append("<table><tr><th>Competencia</th><th>Promedio</th><th>Frecuencia</th></tr>")
            for (c in comps) {
                val cColor = when { c.promedio >= 7.0 -> "#34d399"; c.promedio >= 4.0 -> "#fbbf24"; else -> "#f87171" }
                sb.append("<tr><td>${escHtml(c.competencia)}</td><td class=\"num\" style=\"color:$cColor\">${"%.1f".format(c.promedio)}/9</td><td class=\"num\">${c.count}</td></tr>")
            }
            sb.append("</table>")
            if (areas.isNotEmpty()) {
                sb.append("<div style=\"margin:6px 0;display:flex;gap:6px;flex-wrap:wrap\">")
                areas.entries.sortedByDescending { it.value }.forEach { (word, freq) ->
                    sb.append("<span style=\"background:#1a2142;color:#818cf8;padding:2px 10px;border-radius:12px;font-size:11px;border:1px solid #2d3a6a\">${escHtml(word)} ($freq)</span>")
                }
                sb.append("</div>")
            }
            if (evals.isNotEmpty()) {
                sb.append("<table style=\"margin-top:6px\"><tr><th>Fecha</th><th>Entorno</th><th>Asunto</th><th>Calif</th></tr>")
                for (ev in evals) {
                    sb.append("<tr><td>${escHtml(ev.fechaEvaluacion.take(10))}</td><td>${escHtml(ev.entornoClinico)}</td><td>${escHtml(ev.asuntoPrincipal.take(40))}</td><td class=\"num\" style=\"color:${scoreColor(ev.calificacionTotal)}\">${"%.1f".format(ev.calificacionTotal)}/10</td></tr>")
                }
                sb.append("</table>")
            }
        }
        sb.append("</div>")

        // Tab 3: Evaluaciones
        sb.append("""<div id="tab-evaluaciones" class="tab-content">""")
        var totalEvals = 0
        for (a in alumnos) {
            val evals = a.evaluaciones ?: continue
            sb.append("<h3>${escHtml(a.nombreCompleto)} · ${escHtml(a.matricula)}</h3><table><tr><th>#</th><th>Fecha</th><th>Entorno</th><th>Paciente</th><th>Asunto</th><th>Complejidad</th><th>T.Obs</th><th>T.Fbk</th><th>Calificación</th></tr>")
            evals.forEachIndexed { i, ev ->
                totalEvals++
                sb.append("<tr><td class=\"num\">${i+1}</td><td>${escHtml(ev.fechaEvaluacion.take(10))}</td><td>${escHtml(ev.entornoClinico)}</td><td>${escHtml(ev.tipoPaciente)}</td><td>${escHtml(ev.asuntoPrincipal.take(35))}</td><td>${escHtml(ev.complejidad)}</td><td class=\"num\">${ev.tiempoObservacion}</td><td class=\"num\">${ev.tiempoFeedback}</td><td class=\"num\" style=\"color:${scoreColor(ev.calificacionTotal)}\">${"%.1f".format(ev.calificacionTotal)}/10</td></tr>")
            }
            sb.append("</table>")
        }
        if (totalEvals == 0) sb.append("<div class=\"info\">Sin evaluaciones registradas</div>")
        sb.append("</div>")

        // Tab 4: Rúbricas
        sb.append("""<div id="tab-rubricas" class="tab-content">""")
        var totalRubrics = 0
        for (a in alumnos) {
            val evals = a.evaluaciones ?: continue
            evals.forEachIndexed { ei, ev ->
                val detalles = ev.detalles ?: return@forEachIndexed
                if (detalles.isEmpty()) return@forEachIndexed
                totalRubrics++
                sb.append("<h3>${escHtml(a.nombreCompleto)} · Eval #${ei+1} — ${escHtml(ev.fechaEvaluacion.take(10))}</h3><table><tr><th>Competencia</th><th>Puntaje</th><th>Notas</th><th>A destacar</th><th>A mejorar</th></tr>")
                for (d in detalles) {
                    val dColor = when { d.puntaje >= 7 -> "#34d399"; d.puntaje >= 4 -> "#fbbf24"; else -> "#f87171" }
                    sb.append("<tr><td>${escHtml(d.competencia)}</td><td class=\"num\" style=\"color:$dColor;font-weight:700\">${d.puntaje}/9</td><td style=\"max-width:200px;white-space:normal\">${escHtml(d.notas ?: "")}</td><td style=\"max-width:180px;white-space:normal;color:#34d399\">${escHtml(d.aDestacar ?: "")}</td><td style=\"max-width:180px;white-space:normal;color:#f87171\">${escHtml(d.aMejorar ?: "")}</td></tr>")
                }
                sb.append("</table>")
            }
        }
        if (totalRubrics == 0) sb.append("<div class=\"info\">Sin rúbricas registradas</div>")
        sb.append("</div>")

        sb.append("</body></html>")
        return sb.toString()
    }

    // ── Student Report HTML ──────────────────────────────────────────────────

    private fun generateStudentReportHtml(report: StudentReportResponse?): String {
        val sb = StringBuilder()
        sb.append(htmlHeader())

        val student = report?.student
        val evals = report?.evaluaciones ?: emptyList()
        val comps = report?.competencias ?: emptyList()
        val idx = report?.indices

        sb.append("""
<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin-bottom:16px;margin-top:8px">""".trimIndent())
        val statCards = listOf(
            pair("Evaluaciones", "${evals.size}", "#818cf8"),
            pair("Promedio", idx?.let { "${"%.1f".format(it.promedioDisplay)}/10" } ?: "—", scoreColor(idx?.promedioDisplay ?: 0.0)),
            pair("Evolución", idx?.trendText ?: "—", trendColor(idx?.trend ?: 0.0)),
            pair("Regularidad", idx?.consistenciaText ?: "Sin datos", "#64748b"),
        )
        for ((lbl, v, clr) in statCards) {
            sb.append("<div class=\"stat-card\"><div class=\"label\">$lbl</div><div class=\"value\" style=\"color:$clr\">$v</div></div>")
        }
        sb.append("</div>")

        // Info
        sb.append("<table><tr><th>Matrícula</th><th>Grupo</th><th>Docente</th><th>Principal fortaleza</th><th>Área prioritaria</th><th>Cambio desde el inicio</th></tr>")
        sb.append("<tr><td>${escHtml(student?.matricula ?: "")}</td><td>${escHtml(student?.semestreGrupo ?: "")}</td><td>${escHtml(student?.docenteNombre ?: "")}</td>")
        sb.append("<td style=\"color:#34d399\">${escHtml(idx?.competenciaFuerte ?: "—")}</td><td style=\"color:#f87171\">${escHtml(idx?.competenciaDebil ?: "—")}</td>")
        val prog = idx?.progreso ?: 0.0
        val progColor = if (prog > 0) "#34d399" else if (prog < 0) "#f87171" else "#fbbf24"
        sb.append("<td class=\"num\" style=\"color:$progColor\">${if (prog>0)"+" else ""}${"%.1f".format(prog)}</td></tr>")
        sb.append("</table>")

        // Areas de mejora
        val areas = idx?.topAreasMejora ?: emptyMap()
        if (areas.isNotEmpty()) {
            sb.append("<div style=\"margin:10px 0;display:flex;gap:6px;flex-wrap:wrap\">")
            areas.entries.sortedByDescending { it.value }.forEach { (word, freq) ->
                sb.append("<span style=\"background:#1a2142;color:#818cf8;padding:2px 10px;border-radius:12px;font-size:11px;border:1px solid #2d3a6a\">${escHtml(word)} ($freq)</span>")
            }
            sb.append("</div>")
        }

        // Evaluations
        sb.append("<h3>Evaluaciones (${evals.size})</h3>")
        if (evals.isNotEmpty()) {
            sb.append("<table><tr><th>#</th><th>Fecha</th><th>Evaluador</th><th>Entorno</th><th>Paciente</th><th>Asunto</th><th>Complejidad</th><th>T.Obs</th><th>T.Fbk</th><th>Calificación</th></tr>")
            evals.forEachIndexed { i, ev ->
                sb.append("<tr><td class=\"num\">${i+1}</td><td>${escHtml(ev.fechaEvaluacion.take(10))}</td><td>${escHtml(ev.evaluadorNombre ?: "")}</td><td>${escHtml(ev.entornoClinico)}</td><td>${escHtml(ev.tipoPaciente)}</td><td>${escHtml(ev.asuntoPrincipal.take(35))}</td><td>${escHtml(ev.complejidad)}</td><td class=\"num\">${ev.tiempoObservacion}</td><td class=\"num\">${ev.tiempoFeedback}</td><td class=\"num\" style=\"color:${scoreColor(ev.calificacionTotal)}\">${"%.1f".format(ev.calificacionTotal)}/10</td></tr>")
            }
            sb.append("</table>")
        }

        // Competencies
        if (comps.isNotEmpty()) {
            sb.append("<h3>Competencias</h3><table><tr><th>Competencia</th><th>Promedio/9</th><th>Frecuencia</th></tr>")
            for (c in comps) {
                val cColor = when { c.promedio >= 7.0 -> "#34d399"; c.promedio >= 4.0 -> "#fbbf24"; else -> "#f87171" }
                sb.append("<tr><td>${escHtml(c.competencia)}</td><td class=\"num\" style=\"color:$cColor\">${"%.1f".format(c.promedio)}</td><td class=\"num\">${c.count}</td></tr>")
            }
            sb.append("</table>")
        }

        // Rubrics
        sb.append("<h3>Detalle de Rúbricas</h3>")
        var totalR = 0
        evals.forEachIndexed { ei, ev ->
            val detalles = ev.detalles ?: return@forEachIndexed
            if (detalles.isEmpty()) return@forEachIndexed
            totalR++
            sb.append("<h4 style=\"color:#94a3b8;font-size:12px;margin:8px 0 4px\">Eval #${ei+1} — ${escHtml(ev.fechaEvaluacion.take(10))}</h4>")
            sb.append("<table><tr><th>Competencia</th><th>Puntaje</th><th>Notas</th><th>A destacar</th><th>A mejorar</th></tr>")
            for (d in detalles) {
                val dColor = when { d.puntaje >= 7 -> "#34d399"; d.puntaje >= 4 -> "#fbbf24"; else -> "#f87171" }
                sb.append("<tr><td>${escHtml(d.competencia)}</td><td class=\"num\" style=\"color:$dColor;font-weight:700\">${d.puntaje}/9</td><td style=\"max-width:200px;white-space:normal\">${escHtml(d.notas ?: "")}</td><td style=\"max-width:180px;white-space:normal;color:#34d399\">${escHtml(d.aDestacar ?: "")}</td><td style=\"max-width:180px;white-space:normal;color:#f87171\">${escHtml(d.aMejorar ?: "")}</td></tr>")
            }
            sb.append("</table>")
        }
        if (totalR == 0) sb.append("<div class=\"info\">Sin rúbricas registradas</div>")

        sb.append("</body></html>")
        return sb.toString()
    }

    // ── Shared HTML helpers ───────────────────────────────────────────────────

    private fun htmlHeader(): String = """
<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#0a0e1a;color:#e2e8f0;font-family:'Segoe UI',system-ui,sans-serif;font-size:13px;overflow-x:hidden}
.tabs{display:flex;gap:0;background:#0f1322;border-bottom:1px solid #1e2a4a;position:sticky;top:0;z-index:10;overflow-x:auto}
.tab-btn{padding:10px 18px;cursor:pointer;border:none;background:transparent;color:#64748b;font-size:12px;font-weight:600;white-space:nowrap;border-bottom:2px solid transparent;transition:.15s}
.tab-btn:hover{color:#94a3b8;background:#141a2e}
.tab-btn.active{color:#818cf8;border-bottom-color:#818cf8;background:#0f1322}
.tab-content{display:none;padding:12px}
.tab-content.active{display:block}
table{width:100%;border-collapse:collapse;font-size:12px;min-width:600px}
th{background:#1a2142;color:#818cf8;padding:8px 10px;text-align:left;font-weight:600;white-space:nowrap;border:1px solid #1e2a4a}
td{padding:6px 10px;border:1px solid #1a2240;white-space:nowrap}
tr:nth-child(even){background:#0d1225}
tr:hover{background:#141b35}
.num{text-align:right;font-family:'JetBrains Mono','Cascadia Code',monospace}
.info{color:#64748b;font-size:12px;text-align:center;padding:30px}
.stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin-bottom:16px}
.stat-card{background:#0f1322;border:1px solid #1e2a4a;border-radius:8px;padding:14px 16px}
.stat-card .label{color:#64748b;font-size:10px;font-weight:700;letter-spacing:.08em;text-transform:uppercase}
.stat-card .value{font-size:22px;font-weight:700;margin-top:4px}
h3{color:#818cf8;font-size:14px;margin:16px 0 8px;padding-bottom:4px;border-bottom:1px solid #1e2a4a}
h4{color:#94a3b8;font-size:12px;margin:8px 0 4px}
</style></head><body>
""".trimIndent()

    private fun tabsHtml(vararg tabs: String): String {
        val names = tabs.toList()
        val ids = listOf("tab-resumen", "tab-alumnos", "tab-evaluaciones", "tab-rubricas")
        val b = StringBuilder("<div class=\"tabs\">")
        for ((i, name) in names.withIndex()) {
            val active = if (i == 0) " active" else ""
            b.append("<button class=\"tab-btn$active\" onclick=\"switchTab('${ids[i]}',this)\">$name</button>")
        }
        b.append("</div><script>function switchTab(id,btn){document.querySelectorAll('.tab-content').forEach(function(t){t.classList.remove('active')});document.querySelectorAll('.tab-btn').forEach(function(b){b.classList.remove('active')});document.getElementById(id).classList.add('active');btn.classList.add('active');}</script>")
        return b.toString()
    }

    private data class pair(val label: String, val value: String, val color: String)

    private fun escHtml(s: String?): String =
        s?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;")
            ?.replace("\"", "&quot;")?.replace("'", "&#39;") ?: ""

    private fun scoreColor(score: Double): String =
        if (score >= 8.0) "#34d399" else if (score >= 5.0) "#fbbf24" else "#f87171"

    private fun trendColor(trend: Double): String =
        if (trend > 0.5) "#34d399" else if (trend < -0.5) "#f87171" else "#fbbf24"

    private fun progressColor(prog: Double): String =
        if (prog > 2) "#34d399" else if (prog < -2) "#f87171" else "#fbbf24"

    // ── Download XLSX directly from server endpoint ──────────────────────────

    private fun downloadXlsxDirect() {
        Toast.makeText(this, "Descargando reporte…", Toast.LENGTH_SHORT).show()
        val baseUrl = RetrofitClient.BASE_URL.trimEnd('/')
        val url = when (dataType) {
            "student_report" -> "$baseUrl/reports/student/download-xlsx?student_id=${studentReport?.student?.idAlumno ?: studentId}"
            else -> "$baseUrl/reports/teacher-summary/download-xlsx?evaluador_id=$evaluadorId&modo=$modo"
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val prefix = when (dataType) { "student_report" -> "ReporteAlumno" else -> "ReporteDocente" }
                    val file = File(dir, "${prefix}_${sdf.format(Date())}.xlsx")
                    FileOutputStream(file).use { it.write(bytes) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@XlsViewerActivity,
                            "Reporte guardado: ${file.name}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@XlsViewerActivity,
                            "Respuesta vacía del servidor", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@XlsViewerActivity,
                        "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
