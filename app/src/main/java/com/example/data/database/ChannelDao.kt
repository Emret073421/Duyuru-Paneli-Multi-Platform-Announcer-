package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM chat_channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChannel(channel: ChannelEntity)

    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)
}
