package com.projectcenter.app.data.activity

import com.projectcenter.app.ui.activity.ActivityItem
import com.projectcenter.app.ui.activity.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-memory activity feed. Can later be backed by DataStore / Room.
 */
class ActivityRepository {

    private val _items = MutableStateFlow<List<ActivityItem>>(emptyList())
    val items: StateFlow<List<ActivityItem>> = _items.asStateFlow()

    fun addSuccess(projectName: String) {
        prepend(
            ActivityItem(
                id = UUID.randomUUID().toString(),
                title = projectName,
                subtitle = "Build successful",
                timeLabel = "Just now",
                type = ActivityType.SUCCESS
            )
        )
    }

    fun addFailure(projectName: String) {
        prepend(
            ActivityItem(
                id = UUID.randomUUID().toString(),
                title = projectName,
                subtitle = "Build failed",
                timeLabel = "Just now",
                type = ActivityType.FAILED
            )
        )
    }

    fun addArtifact(projectName: String, artifactName: String) {
        prepend(
            ActivityItem(
                id = UUID.randomUUID().toString(),
                title = projectName,
                subtitle = "Artifact available · $artifactName",
                timeLabel = "Just now",
                type = ActivityType.ARTIFACT
            )
        )
    }

    private fun prepend(item: ActivityItem) {
        _items.value = listOf(item) + _items.value.take(49)
    }
}
