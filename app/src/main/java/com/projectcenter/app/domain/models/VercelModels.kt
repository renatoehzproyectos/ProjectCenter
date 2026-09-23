package com.projectcenter.app.domain.models

data class VercelUser(
    val id: String,
    val username: String,
    val email: String?
)

data class VercelProject(
    val id: String,
    val name: String,
    val framework: String?,
    val latestUrl: String?,
    val updatedAt: Long?,
    val linkedRepo: String?
)

data class VercelDeployment(
    val id: String,
    val url: String,
    val state: String?,
    val createdAt: Long?
)
