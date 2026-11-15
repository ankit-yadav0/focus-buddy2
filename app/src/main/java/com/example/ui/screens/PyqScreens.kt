package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.components.NeonPrimaryGradientButton
import com.example.ui.components.NeonSquircleIconBadge
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Study Planner Screen (Panel 6) — Persists study plan in SharedPreferences
 * and lets the user launch a Focus Session directly for their calculated daily target!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlannerScreen(
    onBack: () -> Unit,
    onStartPlannedSession: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE) }

    var totalLecturesText by remember { mutableStateOf(prefs.getString("planner_total_lectures", "40") ?: "40") }
    var hoursPerLectureText by remember { mutableStateOf(prefs.getString("planner_hours_per_lec", "2") ?: "2") }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("planner_playback_speed", 1.5f)) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var foundationalSubject by remember { mutableStateOf(prefs.getBoolean("planner_foundational", true)) }
    var showCalculatedSummary by remember { mutableStateOf(prefs.getBoolean("planner_calculated", true)) }

    val nowMs = remember { System.currentTimeMillis() }
    var startDateMs by remember {
        mutableLongStateOf(prefs.getLong("planner_start_ms", nowMs))
    }
    var endDateMs by remember {
        mutableLongStateOf(prefs.getLong("planner_end_ms", nowMs + 28L * 24 * 3600 * 1000L))
    }

    fun savePlannerState() {
        prefs.edit()
            .putString("planner_total_lectures", totalLecturesText)
            .putString("planner_hours_per_lec", hoursPerLectureText)
            .putFloat("planner_playback_speed", playbackSpeed)
            .putBoolean("planner_foundational", foundationalSubject)
            .putLong("planner_start_ms", startDateMs)
            .putLong("planner_end_ms", endDateMs)
            .putBoolean("planner_calculated", true)
            .apply()
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val startDateStr = remember(startDateMs) { dateFormatter.format(Date(startDateMs)) }
    val endDateStr = remember(endDateMs) { dateFormatter.format(Date(endDateMs)) }

    val lectures = totalLecturesText.toFloatOrNull() ?: 0f
    val hoursPerLec = hoursPerLectureText.toFloatOrNull() ?: 0f
    val rawStudyHours = remember(lectures, hoursPerLec, playbackSpeed, foundationalSubject) {
        val base = (lectures * hoursPerLec) / playbackSpeed.coerceAtLeast(0.5f)
        if (foundationalSubject) base else base * 0.85f
    }
    val totalDays = remember(startDateMs, endDateMs) {
        max(1L, ((endDateMs - startDateMs) / (24L * 3600 * 1000L)).coerceAtLeast(1L))
    }
    val dailyHoursTarget = remember(rawStudyHours, totalDays) {
        rawStudyHours / totalDays.toFloat()
    }
    val dailyMinutesTarget = remember(dailyHoursTarget) {
        (dailyHoursTarget * 60f).roundToInt().coerceIn(15, 360)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Study Planner",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCardBorderBrush, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NeonSquircleIconBadge(
                                icon = Icons.Default.Calculate,
                                tint = NeonCyan,
                                size = 38.dp
                            )
                            Column {
                                Text(
                                    text = "Plan Calculator",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Auto-saved to your phone",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Total Lectures", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = totalLecturesText,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                totalLecturesText = it
                                savePlannerState()
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )

                    Text("Hours per Lecture", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = hoursPerLectureText,
                        onValueChange = {
                            if (it.length <= 4) {
                                hoursPerLectureText = it
                                savePlannerState()
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )

                    Text("Playback Speed", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonSurface)
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable { speedMenuExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false },
                            modifier = Modifier.background(NeonSurface)
                        ) {
                            listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", color = Color.White) },
                                    onClick = {
                                        playbackSpeed = speed
                                        speedMenuExpanded = false
                                        savePlannerState()
                                    }
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Required Study Hours",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", rawStudyHours),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "hours",
                            color = TextSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Plan Duration",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonBlack.copy(alpha = 0.65f))
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = startDateMs }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val chosen = Calendar.getInstance().apply { set(y, m, d) }
                                            startDateMs = chosen.timeInMillis
                                            savePlannerState()
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start Date", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(startDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Start Date",
                                tint = NeonMagenta,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonBlack.copy(alpha = 0.65f))
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = endDateMs }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val chosen = Calendar.getInstance().apply { set(y, m, d) }
                                            endDateMs = chosen.timeInMillis
                                            savePlannerState()
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("End Date", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(endDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "End Date",
                                tint = NeonMagenta,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Foundational Subject (100% buffer)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = foundationalSubject,
                                onCheckedChange = {
                                    foundationalSubject = it
                                    savePlannerState()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonMagenta,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = NeonBlack
                                )
                            )
                        }

                        NeonPrimaryGradientButton(
                            text = "Calculate Plan",
                            onClick = {
                                savePlannerState()
                                showCalculatedSummary = true
                                Toast.makeText(context, "Plan saved: ${String.format(Locale.US, "%.2f", dailyHoursTarget)} hrs/day", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp
                        )

                        if (showCalculatedSummary) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonBlack.copy(alpha = 0.8f)),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Daily Focus Target: ${String.format(Locale.US, "%.2f", dailyHoursTarget)} hrs / day ($dailyMinutesTarget mins)",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Spanning $totalDays days • ${String.format(Locale.US, "%.1f", lectures / totalDays.toFloat())} lectures/day at ${playbackSpeed}x speed",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    NeonOutlinedPillButton(
                                        text = "Start ${dailyMinutesTarget}m Focus Session Now",
                                        leadingIcon = Icons.Default.PlayArrow,
                                        onClick = { onStartPlannedSession(dailyMinutesTarget) },
                                        modifier = Modifier.fillMaxWidth(),
                                        height = 44.dp,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

data class PyqQuestionItem(
    val subject: String,
    val topic: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/**
 * PYQ Practice Screen (Panel 9) — Real Multi-Subject Question Bank + Custom Question Adder
 * + Persists Subject Accuracy to SharedPreferences so Subject Progress in Focus & Analytics updates live!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PyqPracticeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE) }

    var currentStep by remember { mutableIntStateOf(1) } // 1 = Setup, 2 = Quiz, 3 = Results
    var selectedSubject by remember { mutableStateOf("Physics") }
    var subjectExpanded by remember { mutableStateOf(false) }

    val topicsBySubject = remember {
        mapOf(
            "Physics" to listOf("Modern Physics", "Electrostatics", "Current Electricity", "Ray & Wave Optics", "Thermodynamics"),
            "Chemistry" to listOf("Chemical Bonding", "Thermodynamics", "Electrochemistry", "Organic Reaction Mechanisms", "Coordination Compounds"),
            "Mathematics" to listOf("Definite Integration", "Differential Equations", "Matrices & Determinants", "3D Geometry & Vectors", "Probability"),
            "Biology" to listOf("Genetics & Evolution", "Cell Structure & Function", "Human Physiology", "Plant Physiology", "Biotechnology")
        )
    }

    var selectedTopic by remember(selectedSubject) {
        mutableStateOf(topicsBySubject[selectedSubject]?.firstOrNull() ?: "Modern Physics")
    }
    var topicExpanded by remember { mutableStateOf(false) }
    var questionCount by remember { mutableFloatStateOf(10f) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }

    val builtInBank = remember {
        listOf(
            // Physics
            PyqQuestionItem("Physics", "Modern Physics", "If the de Broglie wavelength of a particle is halved, its kinetic energy becomes:", listOf("2 times", "4 times", "Half", "16 times"), 1, "λ = h / √(2mK) ⇒ K ∝ 1/λ². Halving λ quadruples K."),
            PyqQuestionItem("Physics", "Modern Physics", "In a photoelectric experiment, stopping potential V₀ plotted against frequency ν has a slope equal to:", listOf("h", "h / e", "e / h", "h · e"), 1, "eV₀ = hν - φ ⇒ V₀ = (h/e)ν - φ/e, so slope = h/e."),
            PyqQuestionItem("Physics", "Modern Physics", "The half-life of a radioactive nuclide is T. What fraction of the original sample decays in time 3T?", listOf("1/8", "7/8", "3/4", "1/3"), 1, "Remaining N/N₀ = (1/2)³ = 1/8, so decayed fraction = 1 - 1/8 = 7/8."),
            PyqQuestionItem("Physics", "Electrostatics", "Two point charges +q and +4q are separated by distance d. Where on the line joining them is the net electric field zero?", listOf("d/3 from +q", "d/2 from +q", "2d/3 from +q", "d/4 from +q"), 0, "1/x² = 4/(d-x)² ⇒ (d-x)/x = 2 ⇒ x = d/3 from +q."),
            PyqQuestionItem("Physics", "Current Electricity", "When two identical cells of emf E and internal resistance r are connected in parallel across external resistor R, the current is:", listOf("E / (R + r/2)", "2E / (R + 2r)", "E / (R + 2r)", "2E / (R + r/2)"), 0, "Parallel identical cells have equivalent emf E and equivalent internal resistance r/2."),
            PyqQuestionItem("Physics", "Ray & Wave Optics", "When a biconvex lens of glass (μ = 1.5) is immersed in water (μ = 4/3), its focal length in water becomes:", listOf("2 times its focal length in air", "4 times its focal length in air", "Half its focal length in air", "Unchanged"), 1, "1/f ∝ (μ_g/μ_m - 1). In air: 1.5 - 1 = 0.5; in water: 9/8 - 1 = 1/8. Ratio = 4."),
            PyqQuestionItem("Physics", "Thermodynamics", "In an adiabatic process for an ideal monoatomic gas (γ = 5/3), if volume is compressed to 1/8 of initial volume, pressure becomes:", listOf("8 P₀", "16 P₀", "32 P₀", "64 P₀"), 2, "P V^γ = const ⇒ P = P₀ (8)^(5/3) = P₀ · 2⁵ = 32 P₀."),
            // Chemistry
            PyqQuestionItem("Chemistry", "Chemical Bonding", "According to VSEPR theory, the hybridization and shape of XeF₄ are respectively:", listOf("sp³d², Square Planar", "sp³d², Octahedral", "sp³d, See-saw", "sp³, Tetrahedral"), 0, "XeF₄ has 4 bond pairs and 2 lone pairs (steric number 6, sp³d²) → Square Planar."),
            PyqQuestionItem("Chemistry", "Chemical Bonding", "Which of the following species is paramagnetic and has a bond order of 2.5?", listOf("N₂", "O₂⁺", "CO", "NO⁻"), 1, "O₂⁺ has 15 electrons (1 unpaired electron in π*2p) → bond order = (10 - 5)/2 = 2.5."),
            PyqQuestionItem("Chemistry", "Electrochemistry", "How many Faradays of electricity are required to reduce 1 mole of MnO₄⁻ to Mn²⁺ in acidic medium?", listOf("3 F", "5 F", "7 F", "2 F"), 1, "Mn oxidation state changes from +7 to +2, requiring 5 moles of electrons = 5 F."),
            PyqQuestionItem("Chemistry", "Organic Reaction Mechanisms", "Which of the following alkyl halides undergoes SN1 reaction fastest?", listOf("CH₃Cl", "CH₃CH₂Cl", "(CH₃)₂CHCl", "(CH₃)₃CCl"), 3, "SN1 reactivity depends on carbocation stability: 3° carbocation (CH₃)₃C⁺ is most stable."),
            PyqQuestionItem("Chemistry", "Coordination Compounds", "The IUPAC oxidation state and coordination number of Co in [Co(en)₂Cl₂]⁺ are:", listOf("+3 and 6", "+2 and 4", "+3 and 4", "+2 and 6"), 0, "en is a neutral bidentate ligand (2×2 = 4) + 2 Cl⁻ = coordination number 6; charge x - 2 = +1 ⇒ x = +3."),
            // Mathematics
            PyqQuestionItem("Mathematics", "Definite Integration", "The value of ∫₀^(π/2) [√sin x / (√sin x + √cos x)] dx is:", listOf("π / 2", "π / 4", "π", "0"), 1, "By king's property I = ∫₀^(π/2) f(π/2 - x) dx ⇒ 2I = ∫₀^(π/2) 1 dx = π/2 ⇒ I = π/4."),
            PyqQuestionItem("Mathematics", "Matrices & Determinants", "If A is a 3×3 matrix with |A| = 5, then the value of |adj(A)| is:", listOf("5", "25", "125", "15"), 1, "For an n×n matrix, |adj(A)| = |A|^(n-1) = 5^(3-1) = 25."),
            PyqQuestionItem("Mathematics", "Differential Equations", "The integrating factor (I.F.) of dy/dx + (y / x) = x² is:", listOf("e^x", "x", "1 / x", "ln x"), 1, "I.F. = e^(∫ (1/x) dx) = e^(ln x) = x."),
            PyqQuestionItem("Mathematics", "Probability", "If A and B are independent events with P(A) = 0.4 and P(B) = 0.5, then P(A ∪ B) equals:", listOf("0.9", "0.7", "0.2", "0.5"), 1, "P(A ∪ B) = P(A) + P(B) - P(A)P(B) = 0.4 + 0.5 - 0.20 = 0.70."),
            // Biology
            PyqQuestionItem("Biology", "Genetics & Evolution", "In a dihybrid cross between two heterozygotes (AaBb × AaBb), the phenotypic ratio in F₂ generation is:", listOf("3 : 1", "9 : 3 : 3 : 1", "1 : 2 : 1", "1 : 1 : 1 : 1"), 1, "Mendel's law of independent assortment yields a 9:3:3:1 phenotypic ratio."),
            PyqQuestionItem("Biology", "Cell Structure & Function", "During which stage of Meiosis-I does crossing over (recombination) take place?", listOf("Leptotene", "Zygotene", "Pachytene", "Diplotene"), 2, "Crossing over between non-sister chromatids mediated by recombinase occurs in Pachytene."),
            PyqQuestionItem("Biology", "Human Physiology", "Which part of the human brain regulates body temperature, urge for eating, and drinking?", listOf("Cerebellum", "Hypothalamus", "Medulla oblongata", "Pons"), 1, "The hypothalamus contains thermoregulatory and hunger/thirst centers."),
            PyqQuestionItem("Biology", "Biotechnology", "Which enzyme is used to join sticky ends of DNA fragments during recombinant DNA technology?", listOf("Restriction endonuclease", "DNA ligase", "DNA polymerase", "Helicase"), 1, "DNA ligase catalyzes the formation of phosphodiester bonds to ligate DNA fragments.")
        )
    }

    var customQuestions by remember {
        val raw = prefs.getString("custom_pyq_questions", "") ?: ""
        val parsed = raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("|").map { it.trim() }
            if (p.size >= 7) {
                PyqQuestionItem(p[0], p[1], p[2], listOf(p[3], p[4], p[5], p[6]), p.getOrNull(7)?.toIntOrNull() ?: 0, p.getOrNull(8) ?: "")
            } else null
        }
        mutableStateOf(parsed)
    }

    val activeQuizQuestions = remember(selectedSubject, selectedTopic, questionCount, customQuestions) {
        val all = customQuestions + builtInBank
        val topicMatches = all.filter { it.subject == selectedSubject && it.topic == selectedTopic }
        val subjectMatches = all.filter { it.subject == selectedSubject }
        val pool = if (topicMatches.size >= 3) topicMatches else if (subjectMatches.isNotEmpty()) subjectMatches else all
        val targetSize = questionCount.toInt().coerceAtMost(pool.size).coerceAtLeast(1)
        pool.take(targetSize)
    }

    var currentQuestionIdx by remember { mutableIntStateOf(0) }
    var selectedOptionIdx by remember { mutableIntStateOf(-1) }
    var isAnswerChecked by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PYQ Practice",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) currentStep = 1 else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddQuestionDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add PYQ", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1 Setup — 2 Quiz — 3 Results Stepper
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PyqStepCircle(number = 1, label = "Setup", active = currentStep == 1, done = currentStep > 1)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .padding(horizontal = 8.dp)
                                .background(if (currentStep > 1) NeonCyan else Color(0xFF2E3248))
                        )
                        PyqStepCircle(number = 2, label = "Quiz", active = currentStep == 2, done = currentStep > 2)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .padding(horizontal = 8.dp)
                                .background(if (currentStep > 2) NeonCyan else Color(0xFF2E3248))
                        )
                        PyqStepCircle(number = 3, label = "Results", active = currentStep == 3, done = false)
                    }

                    when (currentStep) {
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select Subject", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NeonSurface)
                                            .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                                            .clickable { subjectExpanded = true }
                                            .padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedSubject, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = subjectExpanded,
                                        onDismissRequest = { subjectExpanded = false },
                                        modifier = Modifier.background(NeonSurface)
                                    ) {
                                        topicsBySubject.keys.forEach { subj ->
                                            DropdownMenuItem(
                                                text = { Text(subj, color = Color.White) },
                                                onClick = {
                                                    selectedSubject = subj
                                                    subjectExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select Topic", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NeonSurface)
                                            .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                                            .clickable { topicExpanded = true }
                                            .padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedTopic, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = topicExpanded,
                                        onDismissRequest = { topicExpanded = false },
                                        modifier = Modifier.background(NeonSurface)
                                    ) {
                                        (topicsBySubject[selectedSubject] ?: emptyList()).forEach { topic ->
                                            DropdownMenuItem(
                                                text = { Text(topic, color = Color.White) },
                                                onClick = {
                                                    selectedTopic = topic
                                                    topicExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Number of Questions", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Slider(
                                        value = questionCount,
                                        onValueChange = { questionCount = it },
                                        valueRange = 3f..20f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonMagenta,
                                            activeTrackColor = NeonMagenta,
                                            inactiveTrackColor = NeonSurface
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${questionCount.toInt()}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            NeonPrimaryGradientButton(
                                text = "Start Quiz (${activeQuizQuestions.size} Qs)",
                                onClick = {
                                    currentQuestionIdx = 0
                                    selectedOptionIdx = -1
                                    isAnswerChecked = false
                                    correctCount = 0
                                    currentStep = 2
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 56.dp,
                                fontSize = 17.sp
                            )
                        }

                        2 -> {
                            val item = activeQuizQuestions[currentQuestionIdx % activeQuizQuestions.size]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, NeonCardBorderBrush, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Q${currentQuestionIdx + 1} of ${activeQuizQuestions.size} • ${item.subject}",
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = item.topic,
                                            color = NeonMagenta,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = item.question,
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 22.sp
                                    )
                                    item.options.forEachIndexed { idx, opt ->
                                        val isSelected = selectedOptionIdx == idx
                                        val isCorrectOpt = idx == item.correctIndex
                                        val borderColor = when {
                                            !isAnswerChecked && isSelected -> NeonCyan
                                            isAnswerChecked && isCorrectOpt -> Color(0xFF00E676)
                                            isAnswerChecked && isSelected && !isCorrectOpt -> NeonMagenta
                                            else -> Color(0xFF2E3248)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) borderColor.copy(alpha = 0.16f) else NeonBlack.copy(alpha = 0.6f))
                                                .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
                                                .clickable(enabled = !isAnswerChecked) { selectedOptionIdx = idx }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "${('A' + idx)}.",
                                                color = borderColor,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = opt,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    if (isAnswerChecked && item.explanation.isNotBlank()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = NeonBlack.copy(alpha = 0.7f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Solution: ${item.explanation}",
                                                color = NeonCyan,
                                                fontSize = 12.5.sp,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    NeonPrimaryGradientButton(
                                        text = when {
                                            !isAnswerChecked -> "Check Answer"
                                            currentQuestionIdx + 1 < activeQuizQuestions.size -> "Next Question"
                                            else -> "Finish & Save Accuracy"
                                        },
                                        enabled = selectedOptionIdx >= 0,
                                        onClick = {
                                            if (!isAnswerChecked) {
                                                isAnswerChecked = true
                                                if (selectedOptionIdx == item.correctIndex) {
                                                    correctCount++
                                                }
                                            } else {
                                                if (currentQuestionIdx + 1 < activeQuizQuestions.size) {
                                                    currentQuestionIdx++
                                                    selectedOptionIdx = -1
                                                    isAnswerChecked = false
                                                } else {
                                                    val accuracy = ((correctCount.toFloat() / activeQuizQuestions.size.toFloat()) * 100f).toInt()
                                                    prefs.edit().putInt("subject_progress_$selectedSubject", accuracy).apply()
                                                    currentStep = 3
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        3 -> {
                            val accuracy = ((correctCount.toFloat() / activeQuizQuestions.size.toFloat()) * 100f).toInt()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, NeonCardBorderBrush, RoundedCornerShape(22.dp)),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonSurface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Text(
                                        text = "$selectedSubject • $selectedTopic",
                                        color = NeonMagenta,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Score: $correctCount / ${activeQuizQuestions.size} ($accuracy%)",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp
                                    )
                                    Text(
                                        text = "Your $selectedSubject progress bar in Focus & Analytics has been updated to $accuracy%!",
                                        color = TextSecondary,
                                        fontSize = 12.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    NeonGradientProgressBar(
                                        progress = accuracy / 100f,
                                        modifier = Modifier.fillMaxWidth(),
                                        height = 10.dp
                                    )
                                    NeonOutlinedPillButton(
                                        text = "Practice Another Topic",
                                        onClick = { currentStep = 1 },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NeonCyan, fontWeight = FontWeight.Medium)) {
                            append("Practice today. ")
                        }
                        withStyle(SpanStyle(color = NeonMagenta, fontWeight = FontWeight.Medium)) {
                            append("Score better tomorrow.")
                        }
                    },
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
    }

    if (showAddQuestionDialog) {
        var qText by remember { mutableStateOf("") }
        var optA by remember { mutableStateOf("") }
        var optB by remember { mutableStateOf("") }
        var optC by remember { mutableStateOf("") }
        var optD by remember { mutableStateOf("") }
        var correctIdxText by remember { mutableStateOf("1") }

        AlertDialog(
            onDismissRequest = { showAddQuestionDialog = false },
            containerColor = NeonSurface,
            title = { Text("Add Custom $selectedSubject PYQ", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = qText, onValueChange = { qText = it }, label = { Text("Question") })
                    OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option 1") }, singleLine = true)
                    OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option 2") }, singleLine = true)
                    OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option 3") }, singleLine = true)
                    OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option 4") }, singleLine = true)
                    OutlinedTextField(
                        value = correctIdxText,
                        onValueChange = { correctIdxText = it },
                        label = { Text("Correct Option (1-4)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (qText.isNotBlank() && optA.isNotBlank() && optB.isNotBlank()) {
                            val cIdx = ((correctIdxText.toIntOrNull() ?: 1) - 1).coerceIn(0, 3)
                            val newQ = PyqQuestionItem(
                                subject = selectedSubject,
                                topic = selectedTopic,
                                question = qText.trim(),
                                options = listOf(optA.trim(), optB.trim(), optC.ifBlank { "None" }, optD.ifBlank { "All" }),
                                correctIndex = cIdx,
                                explanation = "Custom question added by you."
                            )
                            val updated = customQuestions + newQ
                            customQuestions = updated
                            val raw = updated.joinToString("\n") {
                                "${it.subject}|${it.topic}|${it.question}|${it.options[0]}|${it.options[1]}|${it.options[2]}|${it.options[3]}|${it.correctIndex}|${it.explanation}"
                            }
                            prefs.edit().putString("custom_pyq_questions", raw).apply()
                            showAddQuestionDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBlack)
                ) {
                    Text("Save PYQ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddQuestionDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PyqStepCircle(
    number: Int,
    label: String,
    active: Boolean,
    done: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> Brush.linearGradient(listOf(NeonMagenta, NeonViolet))
                        done -> Brush.linearGradient(listOf(NeonCyan, NeonViolet))
                        else -> Brush.linearGradient(listOf(NeonSurface, NeonSurface))
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = when {
                        active -> NeonMagenta
                        done -> NeonCyan
                        else -> Color(0xFF2E3248)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Text(
            text = label,
            color = if (active) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}
