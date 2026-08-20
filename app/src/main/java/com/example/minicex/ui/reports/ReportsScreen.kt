package com.example.minicex.ui.reports

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.minicex.XlsViewerActivity
import com.example.minicex.ui.theme.LocalAppColors
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.remote.dto.*
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    fragmentActivity: FragmentActivity,
    onShowError: (String) -> Unit,
    onShowInfo: (String) -> Unit,
    onShowSuccess: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var evaluadorId by remember { mutableIntStateOf(-1) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var currentReport by remember { mutableStateOf<StudentReportResponse?>(null) }
    var isTeacherMode by remember { mutableStateOf(false) }
    var teacherSummary by remember { mutableStateOf<TeacherSummaryResponse?>(null) }
    var currentModo by remember { mutableStateOf("mine") }
    var selectedStudentIdx by remember { mutableIntStateOf(0) }
    var teacherQuery by remember { mutableStateOf("") }
    var isOnline by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    // Init
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
        evaluadorId = prefs.getInt("evaluador_id", -1)
        isOnline = isOnline(ctx)
            loadStudents(ctx, scope, evaluadorId) { students = it }
    }
    LaunchedEffect(Unit) {
        while (true) {
            isOnline = isOnline(ctx)
            delay(4_000)
        }
    }

    val horizontalPadding = when {
        LocalConfiguration.current.screenWidthDp >= 840 -> 40.dp
        LocalConfiguration.current.screenWidthDp >= 600 -> 28.dp
        else -> 16.dp
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 16.dp)
        ) {
            // Header
            Text("REPORTES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isTeacherMode) "Resumen del Docente" else "Análisis de Evaluaciones",
                color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = !isOnline) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Los reportes detallados necesitan conexión. Tus evaluaciones locales siguen disponibles.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (!isOnline) Spacer(Modifier.height(12.dp))

            // Mode toggle card
            ModeToggleCard(
                isTeacherMode = isTeacherMode,
                onToggle = {
                    isTeacherMode = !isTeacherMode
                    if (isTeacherMode) {
                        isLoading = true
                        loadTeacherSummary(ctx, scope, evaluadorId, currentModo) {
                            teacherSummary = it
                            isLoading = false
                        }
                    }
                }
            )
            Spacer(Modifier.height(12.dp))

            if (isTeacherMode) {
                // ── TEACHER SUMMARY MODE ──────────────────────────────────
                TeacherSummaryHeader(
                    modo = currentModo,
                    summary = teacherSummary,
                    onModoChange = {
                        currentModo = if (currentModo == "mine") "all" else "mine"
                        isLoading = true
                        loadTeacherSummary(ctx, scope, evaluadorId, currentModo) {
                            teacherSummary = it
                            isLoading = false
                        }
                    },
                    onOpenViewer = {
                        teacherSummary?.let { s ->
                            val json = Gson().toJson(s)
                            ctx.startActivity(Intent(ctx, XlsViewerActivity::class.java).apply {
                                putExtra("data_type", "teacher_summary")
                                putExtra("teacher_summary_json", json)
                                putExtra("evaluador_id", evaluadorId)
                                putExtra("modo", currentModo)
                            })
                        }
                    },
                    onDownloadXlsx = {
                        downloadXlsx(ctx, scope, "${baseUrl(ctx)}/reports/teacher-summary/download-xlsx?evaluador_id=$evaluadorId&modo=$currentModo", "ReporteDocente", onShowError, onShowSuccess)
                    },
                )

                // Student summary list
                val alumnos = teacherSummary?.alumnos ?: emptyList()
                if (isLoading) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else if (alumnos.isEmpty()) {
                    Text("No hay alumnos con evaluaciones en este modo.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                } else {
                    OutlinedTextField(
                        value = teacherQuery,
                        onValueChange = { teacherQuery = it },
                        label = { Text("Buscar alumno o matrícula") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                    Spacer(Modifier.height(12.dp))
                    val filteredStudents = alumnos.filter { student ->
                        teacherQuery.isBlank() ||
                            student.nombreCompleto.contains(teacherQuery, ignoreCase = true) ||
                            student.matricula.contains(teacherQuery, ignoreCase = true)
                    }.sortedBy { it.nombreCompleto }
                    if (filteredStudents.isEmpty()) {
                        Text(
                            "No se encontraron alumnos con ese criterio.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    filteredStudents.forEach { student ->
                        StudentSummaryCard(
                            student = student,
                            onClick = {
                                // Switch to per-student for details
                                isTeacherMode = false
                                selectedStudentIdx = students.indexOfFirst { it.idAlumno == student.idAlumno }
                                    .takeIf { it >= 0 }?.plus(1) ?: 0
                                currentReport = null
                                isLoading = true
                                loadStudentReport(ctx, scope, student.idAlumno) {
                                    currentReport = it
                                    isLoading = false
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                // ── PER-STUDENT MODE ──────────────────────────────────────
                StudentSelectorCard(
                    students = students,
                    selectedIndex = selectedStudentIdx,
                    selectedReportLabel = currentReport?.student?.let { "${it.nombreCompleto} (${it.matricula})" },
                    onSelect = { idx ->
                        selectedStudentIdx = idx
                        if (idx > 0) {
                            val s = students[idx - 1]
                            currentReport = null
                            isLoading = true
                            loadStudentReport(ctx, scope, s.idAlumno) {
                                currentReport = it
                                isLoading = false
                            }
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                currentReport?.let { report ->
                    // Report content
                    StudentReportContent(
                        report = report,
                        onOpenViewer = {
                            val json = Gson().toJson(report)
                            ctx.startActivity(Intent(ctx, XlsViewerActivity::class.java).apply {
                                putExtra("data_type", "student_report")
                                putExtra("student_report_json", json)
                                putExtra("evaluador_id", evaluadorId)
                                putExtra("student_id", report.student?.idAlumno ?: -1)
                            })
                        },
                        onDownloadXlsx = {
                            val sid = report.student?.idAlumno
                            if (sid != null) {
                                downloadXlsx(ctx, scope, "${baseUrl(ctx)}/reports/student/download-xlsx?student_id=$sid", "ReporteAlumno", onShowError, onShowSuccess)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun baseUrl(ctx: Context) = RetrofitClient.BASE_URL.trimEnd('/')

private fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun loadStudents(ctx: Context, scope: CoroutineScope, evaluadorId: Int, onResult: (List<StudentEntity>) -> Unit) {
    scope.launch(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(ctx.applicationContext)
            val list = db.studentDao().getStudentsForDocente(evaluadorId)
            withContext(Dispatchers.Main) { onResult(list) }
        } catch (_: Exception) {}
    }
}

private fun loadStudentReport(ctx: Context, scope: CoroutineScope, studentId: Int, onResult: (StudentReportResponse?) -> Unit) {
    if (!isOnline(ctx)) { onResult(null); return }
    scope.launch(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.instance.getStudentReport(studentId)
            if (resp.isSuccessful && resp.body()?.success == true) {
                withContext(Dispatchers.Main) { onResult(resp.body()) }
            } else {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { onResult(null) }
        }
    }
}

private fun loadTeacherSummary(ctx: Context, scope: CoroutineScope, evaluadorId: Int, modo: String, onResult: (TeacherSummaryResponse?) -> Unit) {
    if (!isOnline(ctx)) { onResult(null); return }
    scope.launch(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.instance.getTeacherSummary(evaluadorId, modo)
            if (resp.isSuccessful && resp.body()?.success == true) {
                withContext(Dispatchers.Main) { onResult(resp.body()) }
            } else {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { onResult(null) }
        }
    }
}

private fun downloadXlsx(ctx: Context, scope: CoroutineScope, url: String, prefix: String, onError: (String) -> Unit, onSuccess: (String) -> Unit) {
    Toast.makeText(ctx, "Descargando…", Toast.LENGTH_SHORT).show()
    scope.launch(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val bytes = resp.body?.bytes()
            if (bytes != null && bytes.isNotEmpty()) {
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, "${prefix}_${sdf.format(Date())}.xlsx")
                FileOutputStream(file).use { it.write(bytes) }
                withContext(Dispatchers.Main) { onSuccess("Archivo guardado: ${file.name}") }
            } else {
                withContext(Dispatchers.Main) { onError("Respuesta vacía") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError("Error: ${e.message}") }
        }
    }
}

// ── Reusable Components ─────────────────────────────────────────────────────

@Composable
fun StatCard(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.primary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(156.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(8.dp))
}

// ── Mode Toggle ─────────────────────────────────────────────────────────────

@Composable
fun ModeToggleCard(isTeacherMode: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "VISTA DEL REPORTE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReportModeOption(
                    label = "Por alumno",
                    selected = !isTeacherMode,
                    modifier = Modifier.weight(1f),
                    onClick = { if (isTeacherMode) onToggle() }
                )
                ReportModeOption(
                    label = "Resumen docente",
                    selected = isTeacherMode,
                    modifier = Modifier.weight(1f),
                    onClick = { if (!isTeacherMode) onToggle() }
                )
            }
        }
    }
}

@Composable
private fun ReportModeOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }
}

// ── Teacher Summary Header ──────────────────────────────────────────────────

@Composable
fun TeacherSummaryHeader(
    modo: String,
    summary: TeacherSummaryResponse?,
    onModoChange: () -> Unit,
    onOpenViewer: () -> Unit,
    onDownloadXlsx: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RESUMEN DEL DOCENTE", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onModoChange) {
                    Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (modo == "mine") "Mis evaluaciones" else "Todos los alumnos",
                        color = MaterialTheme.colorScheme.primary, fontSize = 11.sp
                    )
                }
            }

            // Stats cards row
            val resumen = summary?.resumen
            if (resumen != null) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    StatCard("ALUMNOS", resumen.totalAlumnos.toString(), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    StatCard("CON EVALS", resumen.alumnosConEvaluaciones.toString(), MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    StatCard("EVALUACIONES", resumen.totalEvaluaciones.toString(), LocalAppColors.current.scoreSatisfactory)
                    Spacer(Modifier.width(8.dp))
                    val promColor = if (resumen.promedioGeneral >= 8.0) LocalAppColors.current.scoreSuperior else if (resumen.promedioGeneral >= 5.0) LocalAppColors.current.scoreSatisfactory else LocalAppColors.current.scoreUnsatisfactory
                    StatCard("PROMEDIO", "${"%.1f".format(resumen.promedioGeneral)}/10", promColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Buttons row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenViewer,
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Fullscreen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver pantalla completa", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onDownloadXlsx,
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Descargar .xlsx", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Student Selector ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSelectorCard(
    students: List<StudentEntity>,
    selectedIndex: Int,
    selectedReportLabel: String? = null,
    onSelect: (Int) -> Unit,
) {
    val placeholder = selectedReportLabel ?: "— Seleccione alumno —"
    val names = listOf(placeholder) + students.map { "${it.nombreCompleto} (${it.matricula})" }
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Seleccionar Alumno", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = names.getOrElse(selectedIndex) { names[0] },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    )
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    names.forEachIndexed { idx, name ->
                        DropdownMenuItem(
                            text = { Text(name, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                expanded = false
                                onSelect(idx)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Student Summary Card (for teacher summary list) ─────────────────────────

@Composable
fun StudentSummaryCard(student: StudentSummaryItem, onClick: () -> Unit) {
    val idx = student.indices
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(student.nombreCompleto, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("MAT. ${student.matricula}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val scoreColor = when { idx.promedioDisplay >= 8.0 -> LocalAppColors.current.scoreSuperior; idx.promedioDisplay >= 5.0 -> LocalAppColors.current.scoreSatisfactory; else -> LocalAppColors.current.scoreUnsatisfactory }
                    Text("${"%.1f".format(idx.promedioDisplay)}/10", color = scoreColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${idx.totalEvaluaciones} eval", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                val tColor = when { idx.trend > 0.5 -> LocalAppColors.current.scoreSuperior; idx.trend < -0.5 -> LocalAppColors.current.scoreUnsatisfactory; else -> LocalAppColors.current.scoreSatisfactory }
                val trendArrow = when { idx.trend > 0.5 -> "↑ "; idx.trend < -0.5 -> "↓ "; else -> "→ " }
                Text("$trendArrow${idx.trendText ?: "Estable"}", color = tColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                val pColor = when { idx.progreso > 2 -> LocalAppColors.current.scoreSuperior; idx.progreso < -2 -> LocalAppColors.current.scoreUnsatisfactory; else -> LocalAppColors.current.scoreSatisfactory }
                val sign = if (idx.progreso > 0) "+" else ""
                Text("Cambio desde el inicio: $sign${"%.1f".format(idx.progreso)}", color = pColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Principal fortaleza:", idx.competenciaFuerte ?: "—", LocalAppColors.current.scoreSuperior)
                    InfoRow("Área prioritaria:", idx.competenciaDebil ?: "—", LocalAppColors.current.scoreUnsatisfactory)
                    InfoRow("Regularidad:", idx.consistenciaText ?: "Sin datos", MaterialTheme.colorScheme.onSurface)

                    val comps = student.competencias ?: emptyList()
                    if (comps.isNotEmpty()) {
                        SectionTitle("COMPETENCIAS")
                        comps.forEach { c ->
                            val cColor = when { c.promedio >= 7.0 -> LocalAppColors.current.scoreSuperior; c.promedio >= 4.0 -> LocalAppColors.current.scoreSatisfactory; else -> LocalAppColors.current.scoreUnsatisfactory }
                            Row {
                                Text(c.competencia, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text("${"%.1f".format(c.promedio)}/9", color = cColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val areas = idx.topAreasMejora ?: emptyMap()
                    if (areas.isNotEmpty()) {
                        SectionTitle("ÁREAS DE MEJORA")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            areas.entries.sortedByDescending { it.value }.take(5).forEach { (w, f) ->
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                                    Text("$w ($f)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClick) {
                        Text("Ver reporte completo del alumno", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ── Per-Student Report Content ──────────────────────────────────────────────

private enum class ReportSection(val label: String) {
    OVERVIEW("Resumen"),
    EVOLUTION("Evolución"),
    COMPETENCIES("Competencias"),
    CASES("Casos y tiempos"),
    IMPROVEMENT("Mejora"),
}

@Composable
private fun ReportSectionSelector(selected: ReportSection, onSelect: (ReportSection) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportSection.entries.forEach { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelect(section) },
                label = { Text(section.label, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    }
}

@Composable
fun StudentReportContent(
    report: StudentReportResponse,
    onOpenViewer: () -> Unit,
    onDownloadXlsx: () -> Unit,
) {
    val idx = report.indices
    val evals = report.evaluaciones ?: emptyList()
    val comps = report.competencias ?: emptyList()
    val complejidad = report.complejidad ?: emptyList()
    var selectedSection by remember(report.student?.idAlumno) { mutableStateOf(ReportSection.OVERVIEW) }

    report.student?.let { student ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            student.nombreCompleto.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(student.nombreCompleto, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${student.matricula}  •  ${student.semestreGrupo}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                idx?.let {
                    val scoreColor = when {
                        it.promedioDisplay >= 8.0 -> LocalAppColors.current.scoreSuperior
                        it.promedioDisplay >= 5.0 -> LocalAppColors.current.scoreSatisfactory
                        else -> LocalAppColors.current.scoreUnsatisfactory
                    }
                    Text(
                        "${"%.1f".format(it.promedioDisplay)}",
                        color = scoreColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Summary stats
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RESUMEN GENERAL", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                StatCard("EVALUACIONES", "${evals.size}", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                StatCard("PROMEDIO", idx?.let { "${"%.1f".format(it.promedioDisplay)}/10" } ?: "—",
                    if (idx?.promedioDisplay != null && idx.promedioDisplay >= 8.0) LocalAppColors.current.scoreSuperior else if (idx?.promedioDisplay != null && idx.promedioDisplay >= 5.0) LocalAppColors.current.scoreSatisfactory else LocalAppColors.current.scoreUnsatisfactory)
                Spacer(Modifier.width(8.dp))
                StatCard("EVOLUCIÓN", idx?.trendText ?: "—",
                    if (idx?.trend != null && idx.trend > 0.5) LocalAppColors.current.scoreSuperior else if (idx?.trend != null && idx.trend < -0.5) LocalAppColors.current.scoreUnsatisfactory else LocalAppColors.current.scoreSatisfactory)
                Spacer(Modifier.width(8.dp))
                StatCard("REGULARIDAD", idx?.consistenciaText ?: "—", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Indices card
    if (idx != null && selectedSection == ReportSection.OVERVIEW) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RESUMEN DEL DESEMPEÑO", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))

                val progColor = when { idx.progreso > 2 -> LocalAppColors.current.scoreSuperior; idx.progreso < -2 -> LocalAppColors.current.scoreUnsatisfactory; else -> LocalAppColors.current.scoreSatisfactory }
                val progSign = if (idx.progreso > 0) "+" else ""

                Column(Modifier.fillMaxWidth()) {
                    // 2-column grid using FlowRow or a custom approach
                    Row {
                        IndexCard("Principal fortaleza", idx.competenciaFuerte ?: "—", LocalAppColors.current.scoreSuperior, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        IndexCard("Área prioritaria", idx.competenciaDebil ?: "—", LocalAppColors.current.scoreUnsatisfactory, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        IndexCard("Cambio desde el inicio", "${idx.progresoText} ($progSign${"%.1f".format(idx.progreso)})", progColor, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        IndexCard("Evaluaciones realizadas", idx.totalEvaluaciones.toString(), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Buttons
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenViewer,
            modifier = Modifier.weight(1f).height(48.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Fullscreen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Ver en Pantalla Completa", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onDownloadXlsx,
            modifier = Modifier.weight(1f).height(48.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Descargar .xlsx", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "EXPLORAR ANÁLISIS",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(6.dp))
    ReportSectionSelector(selectedSection) { selectedSection = it }
    Spacer(Modifier.height(16.dp))

    if (selectedSection == ReportSection.OVERVIEW && idx != null) {
        val insightColor = when {
            idx.progreso > 2 -> LocalAppColors.current.scoreSuperior
            idx.progreso < -2 -> LocalAppColors.current.scoreUnsatisfactory
            else -> LocalAppColors.current.scoreSatisfactory
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("LECTURA RÁPIDA", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                InfoRow("Evolución", idx.trendText ?: "Estable", insightColor)
                InfoRow("Principal fortaleza", idx.competenciaFuerte ?: "Sin datos", LocalAppColors.current.scoreSuperior)
                InfoRow("Área prioritaria", idx.competenciaDebil ?: "Sin datos", LocalAppColors.current.scoreUnsatisfactory)
                InfoRow("Regularidad del desempeño", idx.consistenciaText ?: "Sin datos")
            }
        }
    }

    // ── Charts ─────────────────────────────────────────────────────────────

    // Line chart (evolution)
    if (selectedSection == ReportSection.EVOLUTION && evals.isNotEmpty()) {
        ChartCard("EVOLUCIÓN DE CALIFICACIONES") {
            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        val entries = evals.mapIndexed { i, e -> Entry(i.toFloat(), (e.calificacionTotal / 10.0).toFloat()) }
                        val labels = evals.map { if (it.fechaEvaluacion.length >= 10) it.fechaEvaluacion.substring(5, 10) else "#" }
                        val ds = LineDataSet(entries, "Calificación /10").apply {
                            color = ContextCompat.getColor(ctx, com.example.minicex.R.color.primary)
                            valueTextColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                            lineWidth = 2.5f
                            setCircleColor(ContextCompat.getColor(ctx, com.example.minicex.R.color.secondary))
                            circleRadius = 5f
                            setDrawFilled(true)
                            fillColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.primary)
                            fillAlpha = 30
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        data = LineData(ds)
                        description.isEnabled = false
                        legend.textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                        setTouchEnabled(false)
                        xAxis.apply {
                            valueFormatter = IndexAxisValueFormatter(labels)
                            position = XAxis.XAxisPosition.BOTTOM
                            textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                            granularity = 1f
                            setLabelRotationAngle(45f)
                        }
                        axisLeft.apply {
                            textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                            axisMinimum = 0f; axisMaximum = 10f
                        }
                        axisRight.isEnabled = false
                        animateX(800)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(240.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    // Radar (full width)
    if (selectedSection == ReportSection.COMPETENCIES && comps.isNotEmpty()) {
        ChartCard("DESEMPEÑO POR COMPETENCIA") {
            Text(
                "Promedio de todas las evaluaciones, en escala de 0 a 9.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            comps.sortedByDescending { it.promedio }.forEach { competency ->
                val scoreColor = when {
                    competency.promedio >= 7.0 -> LocalAppColors.current.scoreSuperior
                    competency.promedio >= 4.0 -> LocalAppColors.current.scoreSatisfactory
                    else -> LocalAppColors.current.scoreUnsatisfactory
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        competency.competencia,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${"%.1f".format(competency.promedio)} / 9",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { (competency.promedio / 9.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = scoreColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Pie chart
    if (selectedSection == ReportSection.CASES && complejidad.isNotEmpty()) {
        ChartCard("COMPLEJIDAD") {
            AndroidView(
                factory = { ctx ->
                    val pieChart = PieChart(ctx)
                    pieChart.apply {
                        val map = mutableMapOf("Baja" to 0, "Media" to 0, "Alta" to 0)
                        complejidad.forEach { map[it.complejidad] = (map[it.complejidad] ?: 0) + it.count }
                        val pieEntries = map.filter { it.value > 0 }.map { PieEntry(it.value.toFloat(), it.key) }
                        val ds = PieDataSet(pieEntries, "").apply {
                            colors = listOf(
                                ContextCompat.getColor(ctx, com.example.minicex.R.color.status_superior),
                                ContextCompat.getColor(ctx, com.example.minicex.R.color.status_satisfactory),
                                ContextCompat.getColor(ctx, com.example.minicex.R.color.status_unsatisfactory),
                            )
                            valueTextColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.on_surface)
                            valueFormatter = PercentFormatter(pieChart)
                            sliceSpace = 2f
                        }
                        data = PieData(ds)
                        description.isEnabled = false
                        legend.textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                        isDrawHoleEnabled = true; holeRadius = 40f
                        setHoleColor(ContextCompat.getColor(ctx, com.example.minicex.R.color.card_bg))
                        setCenterText("Complejidad")
                        setCenterTextColor(ContextCompat.getColor(ctx, com.example.minicex.R.color.on_surface))
                        setUsePercentValues(true)
                        setTouchEnabled(false)
                        animateX(800)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
        }
    }

    if (selectedSection == ReportSection.CASES && evals.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))

        // Bar chart (times)
        ChartCard("TIEMPOS (OBSERVACIÓN vs FEEDBACK)") {
            AndroidView(
                factory = { ctx ->
                    BarChart(ctx).apply {
                        val obsEntries = evals.mapIndexed { i, e -> BarEntry(i.toFloat(), e.tiempoObservacion.toFloat()) }
                        val fbkEntries = evals.mapIndexed { i, e -> BarEntry(i.toFloat(), e.tiempoFeedback.toFloat()) }
                        val labels = evals.mapIndexed { i, _ -> "#${i + 1}" }
                        val obsSet = BarDataSet(obsEntries, "Observación").apply { color = ContextCompat.getColor(ctx, com.example.minicex.R.color.primary) }
                        val fbkSet = BarDataSet(fbkEntries, "Feedback").apply { color = ContextCompat.getColor(ctx, com.example.minicex.R.color.secondary) }
                        data = BarData(obsSet, fbkSet).apply { barWidth = 0.3f }
                        description.isEnabled = false
                        legend.textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                        setTouchEnabled(false)
                        xAxis.apply {
                            valueFormatter = IndexAxisValueFormatter(labels)
                            position = XAxis.XAxisPosition.BOTTOM
                            textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary)
                            granularity = 1f
                        }
                        axisLeft.apply { textColor = ContextCompat.getColor(ctx, com.example.minicex.R.color.text_secondary); axisMinimum = 0f }
                        axisRight.isEnabled = false
                        animateY(800)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }
    }

    if (selectedSection == ReportSection.IMPROVEMENT) {
        val areas = idx?.topAreasMejora ?: emptyMap()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ÁREAS DE MEJORA RECURRENTES", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Temas detectados con mayor frecuencia en la retroalimentación.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                if (areas.isEmpty()) {
                    Text("Aún no hay suficientes datos para identificar patrones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    areas.entries.sortedByDescending { it.value }.take(8).forEachIndexed { index, entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(entry.key, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${entry.value} veces", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        if (index < areas.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// ── Chart Card Helper ───────────────────────────────────────────────────────

@Composable
fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Index Card ──────────────────────────────────────────────────────────────

@Composable
fun IndexCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
