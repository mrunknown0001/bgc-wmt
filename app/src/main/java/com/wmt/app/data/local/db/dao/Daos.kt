package com.wmt.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wmt.app.data.local.db.entity.DashboardEntity
import com.wmt.app.data.local.db.entity.NotificationEntity
import com.wmt.app.data.local.db.entity.ProjectEntity
import com.wmt.app.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProjectEntity>)

    @Query("DELETE FROM projects")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<ProjectEntity>) {
        clear()
        upsertAll(items)
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM my_tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TaskEntity>)

    @Query("DELETE FROM my_tasks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<TaskEntity>) {
        clear()
        upsertAll(items)
    }
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NotificationEntity>)

    @Query("UPDATE notifications SET readAt = :readAt WHERE id = :id")
    suspend fun markRead(id: String, readAt: String)

    @Query("UPDATE notifications SET readAt = :readAt WHERE readAt IS NULL")
    suspend fun markAllRead(readAt: String)

    @Query("DELETE FROM notifications")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<NotificationEntity>) {
        clear()
        upsertAll(items)
    }
}

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard WHERE id = 0 LIMIT 1")
    fun observe(): Flow<DashboardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardEntity)
}
