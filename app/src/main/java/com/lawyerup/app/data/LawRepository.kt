package com.lawyerup.app.data

import com.lawyerup.app.data.local.AppDatabase
import com.lawyerup.app.data.local.ClaimEntity
import kotlinx.coroutines.flow.Flow

class LawRepository(private val db: AppDatabase) {
    private val dao = db.lawDao()

    fun allClaims(): Flow<List<ClaimEntity>> = dao.getAllClaims()
    fun claimsByForum(forum: String): Flow<List<ClaimEntity>> = dao.getClaimsByForum(forum)
    fun search(query: String): Flow<List<ClaimEntity>> = dao.searchClaims(query)
    suspend fun claim(id: Long): ClaimEntity? = dao.getClaimById(id)
}
