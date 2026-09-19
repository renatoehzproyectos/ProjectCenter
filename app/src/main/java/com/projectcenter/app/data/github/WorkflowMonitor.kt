package com.projectcenter.app.data.github

import com.projectcenter.app.domain.models.Job
import com.projectcenter.app.domain.models.WorkflowRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Polls GitHub for the latest workflow run and its jobs until completion.
 */
class WorkflowMonitor(
    private val repository: GitHubRepository
) {

    data class MonitorState(
        val run: WorkflowRun?,
        val jobs: List<Job>,
        val isComplete: Boolean,
        val error: String? = null
    )

    /**
     * Emits state every [pollIntervalMs] until the run reaches a terminal conclusion
     * or [maxAttempts] is hit.
     */
    fun monitorLatestRun(
        owner: String,
        repo: String,
        pollIntervalMs: Long = 4000L,
        maxAttempts: Int = 90
    ): Flow<MonitorState> = flow {
        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++
            try {
                val runs = repository.getWorkflowRuns(owner, repo).getOrElse { emptyList() }
                val latest = runs.firstOrNull()
                if (latest == null) {
                    emit(MonitorState(null, emptyList(), isComplete = false))
                    delay(pollIntervalMs)
                    continue
                }

                val jobs = repository.getJobs(owner, repo, latest.id).getOrElse { emptyList() }
                val complete = latest.status == "completed"
                emit(MonitorState(latest, jobs, isComplete = complete))

                if (complete) break
            } catch (e: Exception) {
                emit(MonitorState(null, emptyList(), isComplete = false, error = e.message))
            }
            delay(pollIntervalMs)
        }
    }
}
