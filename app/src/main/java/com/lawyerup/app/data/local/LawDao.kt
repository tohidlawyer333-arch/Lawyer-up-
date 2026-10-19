package com.lawyerup.app.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LawDao {
    @Query("SELECT * FROM claims")
    fun getAllClaims(): Flow<List<ClaimEntity>>

    @Query("SELECT * FROM claims WHERE forum = :forum")
    fun getClaimsByForum(forum: String): Flow<List<ClaimEntity>>

    @Query("SELECT * FROM claims WHERE id = :id")
    suspend fun getClaimById(id: Long): ClaimEntity?

    @Query("SELECT * FROM claims WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR articles LIKE '%' || :query || '%'")
    fun searchClaims(query: String): Flow<List<ClaimEntity>>
}
