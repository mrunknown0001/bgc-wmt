package com.wmt.app.di

import android.content.Context
import androidx.room.Room
import com.wmt.app.data.local.db.WmtDatabase
import com.wmt.app.data.local.db.dao.DashboardDao
import com.wmt.app.data.local.db.dao.NotificationDao
import com.wmt.app.data.local.db.dao.ProjectDao
import com.wmt.app.data.local.db.dao.TaskDao
import com.wmt.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WmtDatabase =
        Room.databaseBuilder(context, WmtDatabase::class.java, Constants.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(db: WmtDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideTaskDao(db: WmtDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideNotificationDao(db: WmtDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideDashboardDao(db: WmtDatabase): DashboardDao = db.dashboardDao()
}
