package com.mantelmakher.crm.data.repository

import com.mantelmakher.crm.data.local.ActivityEntity
import com.mantelmakher.crm.data.local.ClientEntity
import com.mantelmakher.crm.data.local.DealEntity
import com.mantelmakher.crm.data.local.PropertyEntity
import com.mantelmakher.crm.data.local.PropertyPhotoEntity
import com.mantelmakher.crm.data.local.TaskEntity
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.Client
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.model.Deal
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyPhoto

fun ClientEntity.toModel() = Client(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    company = company,
    kind = kind,
    source = source,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Client.toEntity() = ClientEntity(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    company = company,
    kind = kind,
    source = source,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun PropertyEntity.toModel() = Property(
    id = id,
    title = title,
    purpose = purpose,
    type = type,
    status = status,
    address = address,
    city = city,
    price = price,
    currency = currency,
    areaSqm = areaSqm,
    rooms = rooms,
    floor = floor,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Property.toEntity() = PropertyEntity(
    id = id,
    title = title,
    purpose = purpose,
    type = type,
    status = status,
    address = address,
    city = city,
    price = price,
    currency = currency,
    areaSqm = areaSqm,
    rooms = rooms,
    floor = floor,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun PropertyPhotoEntity.toModel() = PropertyPhoto(
    id = id,
    propertyId = propertyId,
    fileName = fileName,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun PropertyPhoto.toEntity() = PropertyPhotoEntity(
    id = id,
    propertyId = propertyId,
    fileName = fileName,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun DealEntity.toModel() = Deal(
    id = id,
    title = title,
    clientId = clientId,
    propertyId = propertyId,
    stage = stage,
    amount = amount,
    currency = currency,
    expectedCloseAt = expectedCloseAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Deal.toEntity() = DealEntity(
    id = id,
    title = title,
    clientId = clientId,
    propertyId = propertyId,
    stage = stage,
    amount = amount,
    currency = currency,
    expectedCloseAt = expectedCloseAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun TaskEntity.toModel() = CrmTask(
    id = id,
    title = title,
    details = details,
    dueAt = dueAt,
    reminderAt = reminderAt,
    completedAt = completedAt,
    clientId = clientId,
    dealId = dealId,
    propertyId = propertyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun CrmTask.toEntity() = TaskEntity(
    id = id,
    title = title,
    details = details,
    dueAt = dueAt,
    reminderAt = reminderAt,
    completedAt = completedAt,
    clientId = clientId,
    dealId = dealId,
    propertyId = propertyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun ActivityEntity.toModel() = ActivityEntry(
    id = id,
    kind = kind,
    body = body,
    clientId = clientId,
    dealId = dealId,
    propertyId = propertyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun ActivityEntry.toEntity() = ActivityEntity(
    id = id,
    kind = kind,
    body = body,
    clientId = clientId,
    dealId = dealId,
    propertyId = propertyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
