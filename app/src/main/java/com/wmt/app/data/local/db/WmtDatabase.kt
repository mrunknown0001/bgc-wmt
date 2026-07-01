package com.wmt.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wmt.app.data.local.db.dao.DashboardDao
import com.wmt.app.data.local.db.dao.NotificationDao
import com.wmt.app.data.local.db.dao.ProjectDao
import com.wmt.app.data.local.db.dao.TaskDao
import com.wmt.app.data.local.db.entity.DashboardEntity
import com.wmt.app.data.local.db.entity.NotificationEntity
import com.wmt.app.data.local.db.entity.ProjectEntity
import com.wmt.app.data.local.db.entity.TaskEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        NotificationEntity::class,
        DashboardEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WmtDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun notificationDao(): NotificationDao
    abstract fun dashboardDao(): DashboardDao
}
