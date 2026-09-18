package com.wmt.app.di

import com.wmt.app.data.repository.ApprovalRepositoryImpl
import com.wmt.app.data.repository.AuthRepositoryImpl
import com.wmt.app.data.repository.DashboardRepositoryImpl
import com.wmt.app.data.repository.NotificationRepositoryImpl
import com.wmt.app.data.repository.ProjectRepositoryImpl
import com.wmt.app.data.repository.SettingsRepositoryImpl
import com.wmt.app.data.repository.TaskRepositoryImpl
import com.wmt.app.data.repository.TodoRepositoryImpl
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.domain.repository.AuthRepository
import com.wmt.app.domain.repository.DashboardRepository
import com.wmt.app.domain.repository.NotificationRepository
import com.wmt.app.domain.repository.ProjectRepository
import com.wmt.app.domain.repository.SettingsRepository
import com.wmt.app.domain.repository.TaskRepository
import com.wmt.app.domain.repository.TodoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindTodoRepository(impl: TodoRepositoryImpl): TodoRepository

    @Binds
    @Singleton
    abstract fun bindApprovalRepository(impl: ApprovalRepositoryImpl): ApprovalRepository
}
