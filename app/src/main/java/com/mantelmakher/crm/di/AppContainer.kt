package com.mantelmakher.crm.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.mantelmakher.crm.data.local.CrmDatabase
import com.mantelmakher.crm.data.photos.PhotoStore
import com.mantelmakher.crm.data.preferences.UserPreferences
import com.mantelmakher.crm.data.reminder.AlarmReminderScheduler
import com.mantelmakher.crm.data.repository.RoomActivityRepository
import com.mantelmakher.crm.data.repository.RoomClientRepository
import com.mantelmakher.crm.data.repository.RoomDealRepository
import com.mantelmakher.crm.data.repository.RoomPropertyRepository
import com.mantelmakher.crm.data.repository.RoomTaskRepository
import com.mantelmakher.crm.data.sample.SampleSeeder
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.ClientRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.domain.repository.UserSettings

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = CrmDatabase.create(appContext)
    val photos = PhotoStore(appContext)
    val settings: UserSettings = UserPreferences(appContext)
    val clients: ClientRepository = RoomClientRepository(database.clientDao())
    val properties: PropertyRepository = RoomPropertyRepository(
        database.propertyDao(),
        database.photoDao(),
        photos,
    )
    val deals: DealRepository = RoomDealRepository(
        database.dealDao(),
        database.clientDao(),
        database.propertyDao(),
    )
    val tasks: TaskRepository = RoomTaskRepository(
        database.taskDao(),
        AlarmReminderScheduler(appContext),
    )
    val activities: ActivityRepository = RoomActivityRepository(database.activityDao())
    val sample = SampleSeeder(clients, properties, deals, tasks, activities, photos)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided")
}
