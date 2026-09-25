package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alya.agent.AgentMode
import com.example.alya.agent.AgentResult
import com.example.alya.agent.AgentStep
import com.example.alya.agent.AgentStepStatus
import com.example.alya.agent.BenchmarkSuiteReport
import com.example.alya.agent.JarvisMacroProtocol
import com.example.alya.agent.JarvisTelemetry
import com.example.alya.agent.MacroExecutionReport
import com.example.alya.memory.ExtractedFact
import com.example.alya.memory.ScoredMemory
import com.example.ui.viewmodel.AlyaViewModel
import kotlinx.coroutines.launch

/**
 * AiStudioLabScreen
 *
 * Super advanced AI Reasoning Studio, JARVIS Telemetry & Macros, Extreme Memory Inspector,
 * and Real-World AI Benchmark testing suite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioLabScreen(
    viewModel: AlyaViewModel,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Reasoning Agent", "JARVIS HUD", "Extreme Memory", "AI Benchmark Lab")

    // Reasoning State
    var promptInput by remember { mutableStateOf("Check system status, evaluate free storage, and verify battery health.") }
    var selectedAgentMode by remember { mutableStateOf(AgentMode.REACT_AGENT) }
    var isExecutingPlan by remember { mutableStateOf(false) }
    var currentAgentResult by remember { mutableStateOf<AgentResult?>(null) }
    var liveSteps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }

    // JARVIS Telemetry & Macros State
    var telemetry by remember { mutableStateOf<JarvisTelemetry?>(null) }
    var jarvisBriefing by remember { mutableStateOf<String?>(null) }
    var macroReport by remember { mutableStateOf<MacroExecutionReport?>(null) }
    var isExecutingMacro by remember { mutableStateOf(false) }

    // Memory Inspector State
    var memoryQuery by remember { mutableStateOf("coffee morning routine") }
    var scoredMemories by remember { mutableStateOf<List<ScoredMemory>>(emptyList()) }
    var testExtractionInput by remember { mutableStateOf("Remember that my favorite programming language is Kotlin and I drink espresso at 8 AM.") }
    var extractedFacts by remember { mutableStateOf<List<ExtractedFact>>(emptyList()) }

    // Benchmark Suite State
    var benchmarkReport by remember { mutableStateOf<BenchmarkSuiteReport?>(null) }
    var isRunningBenchmarks by remember { mutableStateOf(false) }

    val refreshTelemetry: () -> Unit = {
        coroutineScope.launch {
            telemetry = viewModel.repository.jarvisTelemetryManager.getSystemTelemetry()
        }
    }

    LaunchedEffect(Unit) {
        refreshTelemetry()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Studio & JARVIS Lab", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Autonomous Agents • Extreme Memory • Benchmarks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("ai_lab_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = refreshTelemetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ReasoningAgentTab(
                    promptInput = promptInput,
                    onPromptChange = { promptInput = it },
                    selectedMode = selectedAgentMode,
                    onModeChange = { selectedAgentMode = it },
                    isExecuting = isExecutingPlan,
                    liveSteps = liveSteps,
                    agentResult = currentAgentResult,
                    onExecute = {
                        isExecutingPlan = true
                        liveSteps = emptyList()
                        currentAgentResult = null
                        coroutineScope.launch {
                            val result = viewModel.repository.agentOrchestrator.execute(
                                prompt = promptInput,
                                mode = selectedAgentMode,
                                onStepProgress = { step ->
                                    liveSteps = liveSteps.filter { it.stepNumber != step.stepNumber } + step
                                }
                            )
                            currentAgentResult = result
                            isExecutingPlan = false
                        }
                    }
                )
                1 -> JarvisHudTab(
                    telemetry = telemetry,
                    briefing = jarvisBriefing,
                    macroReport = macroReport,
                    isExecutingMacro = isExecutingMacro,
                    macros = viewModel.repository.jarvisMacroEngine.predefinedProtocols,
                    onGenerateBriefing = {
                        coroutineScope.launch {
                            jarvisBriefing = viewModel.repository.jarvisTelemetryManager.generateJarvisExecutiveBriefing()
                        }
                    },
                    onRunMacro = { protocol ->
                        isExecutingMacro = true
                        macroReport = null
                        coroutineScope.launch {
                            val rep = viewModel.repository.jarvisMacroEngine.executeProtocol(protocol)
                            macroReport = rep
                            isExecutingMacro = false
                            refreshTelemetry()
                        }
                    }
                )
                2 -> ExtremeMemoryTab(
                    query = memoryQuery,
                    onQueryChange = { memoryQuery = it },
                    scoredMemories = scoredMemories,
                    onSearch = {
                        coroutineScope.launch {
                            scoredMemories = viewModel.repository.extremeMemoryEngine.retrieveMemories(memoryQuery, topK = 6)
                        }
                    },
                    extractionInput = testExtractionInput,
                    onExtractionInputChange = { testExtractionInput = it },
                    extractedFacts = extractedFacts,
                    onExtractFacts = {
                        coroutineScope.launch {
                            val facts = viewModel.repository.extremeMemoryEngine.analyzeAndExtractFacts(testExtractionInput)
                            extractedFacts = facts
                            if (facts.isNotEmpty()) {
                                viewModel.repository.extremeMemoryEngine.saveExtractedFacts(facts)
                            }
                        }
                    }
                )
                3 -> AiBenchmarkTab(
                    report = benchmarkReport,
                    isRunning = isRunningBenchmarks,
                    onRunBenchmarks = {
                        isRunningBenchmarks = true
                        benchmarkReport = null
                        coroutineScope.launch {
                            val rep = viewModel.repository.aiBenchmarkEngine.runFullBenchmarkSuite()
                            benchmarkReport = rep
                            isRunningBenchmarks = false
                        }
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 1: REASONING AGENT & REACT LOOP
// -----------------------------------------------------------------------------------------
@Composable
fun ReasoningAgentTab(
    promptInput: String,
    onPromptChange: (String) -> Unit,
    selectedMode: AgentMode,
    onModeChange: (AgentMode) -> Unit,
    isExecuting: Boolean,
    liveSteps: List<AgentStep>,
    agentResult: AgentResult?,
    onExecute: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Super Advanced Reasoning Orchestrator",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Multi-step goal decomposition, ReAct execution loops, deterministic state verification, and reflection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text("Agent Reasoning Architecture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AgentMode.values().forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = promptInput,
                onValueChange = onPromptChange,
                label = { Text("Goal / Complex User Instruction") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_prompt_input"),
                minLines = 2,
                maxLines = 4
            )
        }

        item {
            Button(
                onClick = onExecute,
                enabled = !isExecuting && promptInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("execute_agent_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reasoning & Executing Plan...")
                } else {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deploy Reasoning Agent")
                }
            }
        }

        if (liveSteps.isNotEmpty()) {
            item {
                Text(
                    "Execution Trace & Step Verification",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(liveSteps.sortedBy { it.stepNumber }) { step ->
                StepCard(step)
            }
        }

        if (agentResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Synthesized Outcome", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(agentResult.replyText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Latency: ${agentResult.trace.totalTimeMs}ms • Tools invoked: ${agentResult.trace.toolsInvoked} • Tokens: ${agentResult.trace.tokensEvaluated}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepCard(step: AgentStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Step ${step.stepNumber}: ${step.description}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                val statusColor = when (step.status) {
                    AgentStepStatus.COMPLETED -> Color(0xFF2E7D32)
                    AgentStepStatus.FAILED -> Color(0xFFC62828)
                    AgentStepStatus.EXECUTING_ACTION -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(step.status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Thought: ${step.thought}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (step.observation != null) {
                Spacer(Modifier.height(4.dp))
                Text("Observation: ${step.observation}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 2: JARVIS HUD & MACRO AUTOMATION
// -----------------------------------------------------------------------------------------
@Composable
fun JarvisHudTab(
    telemetry: JarvisTelemetry?,
    briefing: String?,
    macroReport: MacroExecutionReport?,
    isExecutingMacro: Boolean,
    macros: List<JarvisMacroProtocol>,
    onGenerateBriefing: () -> Unit,
    onRunMacro: (JarvisMacroProtocol) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("JARVIS Executive HUD & Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Proactive hardware state monitoring, thermal status & macro workflows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (telemetry != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryStatItem(icon = Icons.Default.CheckCircle, label = "Battery", value = "${telemetry.batteryPercentage}%", subValue = if (telemetry.isCharging) "Charging" else "${telemetry.batteryTemperatureC}°C")
                            TelemetryStatItem(icon = Icons.Default.Refresh, label = "Network", value = if (telemetry.isWifiConnected) "Wi-Fi" else "Cellular", subValue = "${telemetry.networkLatencyMs}ms ping")
                            TelemetryStatItem(icon = Icons.Default.Info, label = "Storage", value = "${telemetry.availableStorageMb / 1024} GB", subValue = "Free")
                            TelemetryStatItem(icon = Icons.Default.Psychology, label = "RAM", value = "~${telemetry.estimatedAvailableRamMb} MB", subValue = "Available")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onGenerateBriefing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate JARVIS Executive Status Briefing")
            }
        }

        if (briefing != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Executive Voice Briefing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(briefing, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        item {
            Text("Automated Multi-Step JARVIS Protocols", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        items(macros) { macro ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(macro.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(macro.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${macro.actions.size} automated steps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { onRunMacro(macro) },
                        enabled = !isExecutingMacro,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Run", fontSize = 12.sp)
                    }
                }
            }
        }

        if (macroReport != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (macroReport.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Protocol Execution Report", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(macroReport.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryStatItem(icon: ImageVector, label: String, value: String, subValue: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subValue, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -----------------------------------------------------------------------------------------
// TAB 3: EXTREME LONG-TERM MEMORY
// -----------------------------------------------------------------------------------------
@Composable
fun ExtremeMemoryTab(
    query: String,
    onQueryChange: (String) -> Unit,
    scoredMemories: List<ScoredMemory>,
    onSearch: () -> Unit,
    extractionInput: String,
    onExtractionInputChange: (String) -> Unit,
    extractedFacts: List<ExtractedFact>,
    onExtractFacts: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Extreme Long-Term Memory & Recall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Multi-factor ranking (Semantic + Recency + Importance) and auto-fact extraction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Text("Semantic Memory Recall Tester", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Query Memory Bank") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSearch) {
                    Text("Recall")
                }
            }
        }

        if (scoredMemories.isNotEmpty()) {
            items(scoredMemories) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.memory.key, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Score: ${(item.compositeScore * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        Text(item.memory.content, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Semantic: ${(item.semanticScore * 100).toInt()}% | Importance: ${(item.importanceScore * 100).toInt()}% | Recency: ${(item.recencyScore * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Real-Time Fact Extraction Test", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = extractionInput,
                onValueChange = onExtractionInputChange,
                label = { Text("User Conversational Input") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onExtractFacts,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Extract & Consolidate Long-Term Facts")
            }
        }

        if (extractedFacts.isNotEmpty()) {
            items(extractedFacts) { fact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Extracted [${fact.category.displayName}]: ${fact.key}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                        Text(fact.content, style = MaterialTheme.typography.bodySmall)
                        Text("Confidence: ${(fact.confidence * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 4: REAL-WORLD AI BENCHMARK LAB
// -----------------------------------------------------------------------------------------
@Composable
fun AiBenchmarkTab(
    report: BenchmarkSuiteReport?,
    isRunning: Boolean,
    onRunBenchmarks: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Real-World AI Benchmark Suite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Evaluate your AI stack across reasoning, memory, tool matching, state verification and latency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Button(
                onClick = onRunBenchmarks,
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_benchmark_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Running Diagnostic Benchmarks...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Execute Full AI Benchmark Suite")
                }
            }
        }

        if (report != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AI SYSTEM RATING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("${report.overallScore}/100", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        val grade = when {
                            report.overallScore >= 90 -> "AGI-Grade: Production Ready (JARVIS Level)"
                            report.overallScore >= 75 -> "High Performance Intelligent Agent"
                            else -> "Standard Conversational Assistant"
                        }
                        Text(grade, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            items(report.results) { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(res.testName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${res.scoreOutOf100}%", fontWeight = FontWeight.ExtraBold, color = if (res.passed) Color(0xFF2E7D32) else Color(0xFFC62828))
                        }
                        Text("Category: ${res.category} • ${res.latencyMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(4.dp))
                        Text(res.details, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
