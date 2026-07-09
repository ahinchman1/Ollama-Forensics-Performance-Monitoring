package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewState
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model.DashboardViewModel
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.CoroutineContextProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardTelemetryIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var fakeViewState: MutableStateFlow<DashboardViewState>
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        fakeViewState = MutableStateFlow(DashboardViewState.Idle)
        viewModel = object : DashboardViewModel(
            scope = testScope,
            ollamaJobOrchestrator = object : com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ollama.OllamaJobOrchestrator {
                override fun startServer() {}
                override suspend fun runOllamaEssayJob(
                    model: String,
                    prompt: String,
                    onChunk: (String) -> Unit,
                    onTokenProgress: (promptEvalCount: Long, evalCount: Long) -> Unit,
                ) = throw NotImplementedError()
                override fun checkMonitoringToolDependency(): com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result<Unit> = com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result.Success(Unit)
                override fun startDashboard() {}
                override fun startMetricsSampling() {}
                override fun stopMetricsSampling() {}
                override fun captureTmuxPane(targetPane: String) = ""
                override fun resetCollectedMetrics() {}
                override fun resetTimeSeriesSnapshots() {}
                override fun cleanupRuntimeResources() {}
            },
            contextPool = CoroutineContextProviderImpl(),
        ) {
            override val viewState: StateFlow<DashboardViewState> = fakeViewState
        }
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun activeJobState_withMetricsPanel_showsFormattedTelemetryOnScreen() {
        val metricsText = "CPU: 54.0% | Temp: 78°C\nThreads: 4\nCore 0: 81°C / 96.0%"
        fakeViewState.value = DashboardViewState.ActiveJob(
            statusMessage = "Running...",
            metricsPanel = metricsText,
            gpuPanel = "GPU active",
            essayText = "Draft text...",
        )

        composeTestRule.setContent {
            DashboardView(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("CPU: 54.0% | Temp: 78°C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Threads: 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Core 0: 81°C / 96.0%").assertIsDisplayed()
    }
}
