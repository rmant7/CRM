package com.mantelmakher.crm.ui.properties

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.mantelmakher.crm.R
import com.mantelmakher.crm.data.photos.PhotoStore
import com.mantelmakher.crm.di.AppContainer
import com.mantelmakher.crm.di.LocalAppContainer
import com.mantelmakher.crm.domain.logic.MoneyText
import com.mantelmakher.crm.domain.logic.Numbers
import com.mantelmakher.crm.domain.logic.matchesQuery
import com.mantelmakher.crm.domain.model.ActivityEntry
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.DealListItem
import com.mantelmakher.crm.domain.model.Ids
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.Property
import com.mantelmakher.crm.domain.model.PropertyPhoto
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.repository.ActivityRepository
import com.mantelmakher.crm.domain.repository.DealRepository
import com.mantelmakher.crm.domain.repository.PropertyRepository
import com.mantelmakher.crm.domain.repository.TaskRepository
import com.mantelmakher.crm.domain.repository.UserSettings
import com.mantelmakher.crm.ui.components.AddFab
import com.mantelmakher.crm.ui.components.ChipRow
import com.mantelmakher.crm.ui.components.ConfirmDeleteDialog
import com.mantelmakher.crm.ui.components.CrmDropdown
import com.mantelmakher.crm.ui.components.CrmScaffold
import com.mantelmakher.crm.ui.components.EmptyState
import com.mantelmakher.crm.ui.components.HistoryBlock
import com.mantelmakher.crm.ui.components.LoadingBox
import com.mantelmakher.crm.ui.components.LtrTextField
import com.mantelmakher.crm.ui.components.NoteComposer
import com.mantelmakher.crm.ui.components.SearchField
import com.mantelmakher.crm.ui.components.SectionTitle
import com.mantelmakher.crm.ui.components.StageBadge
import com.mantelmakher.crm.ui.components.StatusBadge
import com.mantelmakher.crm.ui.format.dateTimeLabel
import com.mantelmakher.crm.ui.format.decimalLabel
import com.mantelmakher.crm.ui.format.labelRes
import com.mantelmakher.crm.ui.format.moneyLabel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PropertyListFilter { ALL, SALE, RENT, ACTIVE, RESERVED, SOLD }

class PropertiesViewModel(private val repository: PropertyRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(PropertyListFilter.ALL)
    val properties = combine(repository.observe("", null, null, null), query, filter) { all, q, selected ->
        all.filter { matches(it, selected) }.filter { it.matchesQuery(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: PropertyListFilter) { filter.value = value }

    private fun matches(property: Property, selected: PropertyListFilter): Boolean = when (selected) {
        PropertyListFilter.ALL -> true
        PropertyListFilter.SALE -> property.purpose == ListingPurpose.SALE
        PropertyListFilter.RENT -> property.purpose == ListingPurpose.RENT
        PropertyListFilter.ACTIVE -> property.status == PropertyStatus.ACTIVE
        PropertyListFilter.RESERVED -> property.status == PropertyStatus.RESERVED
        PropertyListFilter.SOLD -> property.status == PropertyStatus.SOLD || property.status == PropertyStatus.RENTED
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { PropertiesViewModel(container.properties) }
        }
    }
}

@Composable
fun PropertiesScreen(onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: PropertiesViewModel = viewModel(factory = PropertiesViewModel.factory(container))
    val rows by vm.properties.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filters = PropertyListFilter.entries
    CrmScaffold(title = stringResource(R.string.nav_properties), floatingActionButton = { AddFab(onCreate) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ChipRow(
                options = filters.map { it as PropertyListFilter? },
                selected = filter,
                label = { option -> stringResource(filterLabel(option ?: PropertyListFilter.ALL)) },
                onSelect = { vm.setFilter(it ?: PropertyListFilter.ALL) },
            )
            when {
                rows == null -> LoadingBox()
                rows!!.isEmpty() -> EmptyState(
                    title = stringResource(if (query.isBlank() && filter == PropertyListFilter.ALL) R.string.empty_properties_title else R.string.no_results),
                    body = stringResource(if (query.isBlank() && filter == PropertyListFilter.ALL) R.string.empty_properties_body else R.string.no_results_body),
                    actionLabel = if (query.isBlank() && filter == PropertyListFilter.ALL) stringResource(R.string.add_property) else null,
                    onAction = if (query.isBlank() && filter == PropertyListFilter.ALL) onCreate else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(rows!!, key = { it.id }) { property ->
                        PropertyCard(property) { onOpen(property.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: PropertyListFilter): Int = when (filter) {
    PropertyListFilter.ALL -> R.string.filter_all
    PropertyListFilter.SALE -> R.string.purpose_sale
    PropertyListFilter.RENT -> R.string.purpose_rent
    PropertyListFilter.ACTIVE -> R.string.status_active
    PropertyListFilter.RESERVED -> R.string.status_reserved
    PropertyListFilter.SOLD -> R.string.status_sold
}

@Composable
private fun PropertyCard(property: Property, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(property.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusBadge(property.status)
            }
            Text(
                moneyLabel(property.price, property.currency, property.purpose == ListingPurpose.RENT),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val place = listOf(property.city, property.address).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) Text(place, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(property.type.labelRes()) + " · " + decimalLabel(property.areaSqm) + " " + stringResource(R.string.area_unit) +
                    " · " + decimalLabel(property.rooms) + " " + stringResource(R.string.rooms_unit),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

data class PropertyDetailState(
    val loaded: Boolean = false,
    val property: Property? = null,
    val photos: List<PropertyPhoto> = emptyList(),
    val deals: List<DealListItem> = emptyList(),
    val tasks: List<CrmTask> = emptyList(),
    val history: List<ActivityEntry> = emptyList(),
)

class PropertyDetailViewModel(
    private val properties: PropertyRepository,
    private val deals: DealRepository,
    private val tasks: TaskRepository,
    private val activities: ActivityRepository,
    private val propertyId: String,
) : ViewModel() {
    val state = combine(
        properties.observeById(propertyId),
        properties.observePhotos(propertyId),
        deals.observeForProperty(propertyId),
        tasks.observeForProperty(propertyId),
        activities.observeForProperty(propertyId),
    ) { property, photos, dealRows, taskRows, history ->
        PropertyDetailState(true, property?.takeIf { it.deletedAt == null }, photos, dealRows, taskRows, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PropertyDetailState())

    fun path(fileName: String) = properties.absolutePhotoPath(fileName)

    fun addNote(body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, body.trim(), null, null, propertyId, now, now))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            properties.softDelete(propertyId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, propertyId: String) = viewModelFactory {
            initializer {
                PropertyDetailViewModel(container.properties, container.deals, container.tasks, container.activities, propertyId)
            }
        }
    }
}

@Composable
fun PropertyDetailScreen(
    propertyId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenDeal: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddDeal: (String) -> Unit,
    onAddTask: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: PropertyDetailViewModel = viewModel(factory = PropertyDetailViewModel.factory(container, propertyId))
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val property = state.property
    CrmScaffold(
        title = property?.title ?: stringResource(R.string.nav_properties),
        onBack = onBack,
        actions = {
            if (property != null) {
                IconButton(onClick = { onEdit(property.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit))
                }
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        if (property == null) {
            EmptyState(stringResource(R.string.not_found), stringResource(R.string.no_results_body))
            return@CrmScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (state.photos.isEmpty()) {
                    Text(stringResource(R.string.empty_photos), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.photos, key = { it.id }) { photo ->
                            AsyncImage(
                                model = File(vm.path(photo.fileName)),
                                contentDescription = stringResource(R.string.cd_photo),
                                modifier = Modifier.size(220.dp, 150.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
            item {
                Text(moneyLabel(property.price, property.currency, property.purpose == ListingPurpose.RENT), style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(property.status)
                    Text(stringResource(property.purpose.labelRes()))
                    Text(stringResource(property.type.labelRes()))
                }
                val place = listOf(property.address, property.city).filter { it.isNotBlank() }.joinToString(", ")
                if (place.isNotBlank()) Text(place)
                Text(decimalLabel(property.areaSqm) + " " + stringResource(R.string.area_unit))
                Text(decimalLabel(property.rooms) + " " + stringResource(R.string.rooms_unit))
                if (property.floor.isNotBlank()) Text(stringResource(R.string.floor_unit) + " " + property.floor)
                if (property.description.isNotBlank()) Text(property.description)
            }
            item {
                SectionTitle(stringResource(R.string.linked_deals))
                TextButton(onClick = { onAddDeal(property.id) }) { Text(stringResource(R.string.add_deal)) }
            }
            items(state.deals, key = { it.deal.id }) { item ->
                Card(onClick = { onOpenDeal(item.deal.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.deal.title, modifier = Modifier.weight(1f))
                        StageBadge(item.deal.stage)
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.linked_tasks))
                TextButton(onClick = { onAddTask(property.id) }) { Text(stringResource(R.string.add_task)) }
            }
            items(state.tasks.take(8), key = { it.id }) { task ->
                TextButton(onClick = { onOpenTask(task.id) }) { Text(task.title + " · " + dateTimeLabel(task.dueAt)) }
            }
            item { SectionTitle(stringResource(R.string.history)) }
            item { NoteComposer(vm::addNote) }
            item { HistoryBlock(state.history) }
        }
    }
    if (confirm && property != null) {
        ConfirmDeleteDialog(property.title, onConfirm = { confirm = false; vm.delete(onBack) }, onDismiss = { confirm = false })
    }
}

class PropertyEditorViewModel(
    private val properties: PropertyRepository,
    private val activities: ActivityRepository,
    private val settings: UserSettings,
    private val photosStore: PhotoStore,
    private val propertyId: String?,
) : ViewModel() {
    var title by mutableStateOf("")
    var purpose by mutableStateOf(ListingPurpose.SALE)
    var type by mutableStateOf(PropertyType.APARTMENT)
    var status by mutableStateOf(PropertyStatus.ACTIVE)
    var address by mutableStateOf("")
    var city by mutableStateOf("")
    var price by mutableStateOf("")
    var currency by mutableStateOf("ILS")
    var area by mutableStateOf("")
    var rooms by mutableStateOf("")
    var floor by mutableStateOf("")
    var description by mutableStateOf("")
    var photos by mutableStateOf<List<PropertyPhoto>>(emptyList())
    var titleError by mutableStateOf(false)
    var numberError by mutableStateOf(false)
    var photoFailed by mutableStateOf(false)
    var ready by mutableStateOf(propertyId == null)
    private val draftId = propertyId ?: Ids.new()
    private var createdAt = 0L
    private var existed = false
    private val pending = mutableListOf<String>()
    private var persisted = false

    init {
        viewModelScope.launch {
            if (propertyId == null) {
                val pref = settings.currencyCode.first()
                currency = pref.ifBlank { MoneyText.suggested(Locale.getDefault()) }
                ready = true
            } else {
                val existing = properties.get(propertyId)
                if (existing != null && existing.deletedAt == null) {
                    existed = true
                    createdAt = existing.createdAt
                    title = existing.title
                    purpose = existing.purpose
                    type = existing.type
                    status = existing.status
                    address = existing.address
                    city = existing.city
                    price = if (existing.price == 0L) "" else existing.price.toString()
                    currency = existing.currency
                    area = if (existing.areaSqm == 0.0) "" else trimNumber(existing.areaSqm)
                    rooms = if (existing.rooms == 0.0) "" else trimNumber(existing.rooms)
                    floor = existing.floor
                    description = existing.description
                }
                ready = true
            }
        }
        if (propertyId != null) {
            viewModelScope.launch {
                properties.observePhotos(propertyId).collect { photos = it }
            }
        }
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            val name = Ids.new() + ".jpg"
            val copied = withContext(Dispatchers.IO) {
                runCatching { photosStore.import(uri, name) }.isSuccess
            }
            if (!copied) {
                photoFailed = true
                return@launch
            }
            if (!existed) {
                pending += name
                photos = photos + PropertyPhoto(Ids.new(), "", name, photos.size, 0, 0)
            } else {
                val now = System.currentTimeMillis()
                properties.savePhoto(
                    PropertyPhoto(Ids.new(), draftId, name, properties.nextPhotoSort(draftId), now, now),
                )
            }
        }
    }

    fun removePhoto(photo: PropertyPhoto) {
        viewModelScope.launch {
            if (!existed || photo.propertyId.isEmpty()) {
                pending.remove(photo.fileName)
                photosStore.delete(photo.fileName)
                photos = photos.filterNot { it.id == photo.id }
            } else {
                properties.softDeletePhoto(photo.id, photo.fileName)
            }
        }
    }

    fun path(fileName: String) = properties.absolutePhotoPath(fileName)

    fun save(createdMessage: String, onDone: (String, Boolean) -> Unit) {
        val parsedPrice = Numbers.parseWhole(price)
        val parsedArea = Numbers.parseDecimal(area)
        val parsedRooms = Numbers.parseDecimal(rooms)
        if (title.isBlank()) {
            titleError = true
            return
        }
        if (parsedPrice == null || parsedArea == null || parsedRooms == null) {
            numberError = true
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val created = !existed
            properties.save(
                Property(
                    id = draftId,
                    title = title.trim(),
                    purpose = purpose,
                    type = type,
                    status = status,
                    address = address.trim(),
                    city = city.trim(),
                    price = parsedPrice,
                    currency = currency,
                    areaSqm = parsedArea,
                    rooms = parsedRooms,
                    floor = floor.trim(),
                    description = description.trim(),
                    createdAt = if (existed) createdAt else now,
                    updatedAt = now,
                ),
            )
            pending.forEachIndexed { index, name ->
                properties.savePhoto(PropertyPhoto(Ids.new(), draftId, name, index, now, now))
            }
            pending.clear()
            if (created) {
                activities.add(ActivityEntry(Ids.new(), ActivityKind.NOTE, createdMessage, null, null, draftId, now, now))
            }
            existed = true
            persisted = true
            onDone(draftId, created)
        }
    }

    override fun onCleared() {
        if (!persisted) pending.forEach { photosStore.delete(it) }
    }

    private fun trimNumber(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return value.toString()
    }

    companion object {
        fun factory(container: AppContainer, propertyId: String?) = viewModelFactory {
            initializer {
                PropertyEditorViewModel(
                    container.properties,
                    container.activities,
                    container.settings,
                    container.photos,
                    propertyId,
                )
            }
        }
    }
}

@Composable
fun PropertyEditorScreen(propertyId: String?, onBack: () -> Unit, onSaved: (String, Boolean) -> Unit) {
    val container = LocalAppContainer.current
    val vm: PropertyEditorViewModel = viewModel(factory = PropertyEditorViewModel.factory(container, propertyId))
    val createdMessage = stringResource(R.string.activity_created_property)
    val snackbar = remember { SnackbarHostState() }
    val photoFailedText = stringResource(R.string.photo_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(uri)
    }
    androidx.compose.runtime.LaunchedEffect(vm.photoFailed) {
        if (vm.photoFailed) {
            snackbar.showSnackbar(photoFailedText)
            vm.photoFailed = false
        }
    }
    CrmScaffold(
        title = stringResource(if (propertyId == null) R.string.new_property else R.string.edit_property),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = { TextButton(onClick = { vm.save(createdMessage, onSaved) }) { Text(stringResource(R.string.save)) } },
    ) { padding ->
        if (!vm.ready) {
            LoadingBox(Modifier.padding(padding))
            return@CrmScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle(stringResource(R.string.photos)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.photos, key = { it.id }) { photo ->
                        Box {
                            AsyncImage(
                                model = File(vm.path(photo.fileName)),
                                contentDescription = stringResource(R.string.cd_photo),
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(onClick = { vm.removePhoto(photo) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_photo))
                            }
                        }
                    }
                    item {
                        IconButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_photo))
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    vm.title,
                    { vm.title = it; vm.titleError = false },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.property_title)) },
                    isError = vm.titleError,
                    supportingText = if (vm.titleError) { { Text(stringResource(R.string.title_required)) } } else null,
                    singleLine = true,
                )
            }
            item {
                CrmDropdown(stringResource(R.string.property_purpose), stringResource(vm.purpose.labelRes()), ListingPurpose.entries, { stringResource(it.labelRes()) }, { vm.purpose = it })
            }
            item {
                CrmDropdown(stringResource(R.string.property_type), stringResource(vm.type.labelRes()), PropertyType.entries, { stringResource(it.labelRes()) }, { vm.type = it })
            }
            item {
                CrmDropdown(stringResource(R.string.property_status), stringResource(vm.status.labelRes()), PropertyStatus.entries, { stringResource(it.labelRes()) }, { vm.status = it })
            }
            item {
                OutlinedTextField(vm.address, { vm.address = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.property_address)) }, singleLine = true)
            }
            item {
                OutlinedTextField(vm.city, { vm.city = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.property_city)) }, singleLine = true)
            }
            item {
                LtrTextField(
                    vm.price,
                    { vm.price = it.filter { ch -> ch.isDigit() }.take(12); vm.numberError = false },
                    stringResource(R.string.property_price),
                    isError = vm.numberError,
                    supportingText = if (vm.numberError) stringResource(R.string.number_invalid) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item {
                CrmDropdown(stringResource(R.string.currency), vm.currency, MoneyText.currencies, { it }, { vm.currency = it })
            }
            item {
                LtrTextField(vm.area, { vm.area = it; vm.numberError = false }, stringResource(R.string.property_area), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            item {
                LtrTextField(vm.rooms, { vm.rooms = it; vm.numberError = false }, stringResource(R.string.property_rooms), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            item {
                OutlinedTextField(vm.floor, { vm.floor = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.property_floor)) }, singleLine = true)
            }
            item {
                OutlinedTextField(vm.description, { vm.description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.property_description)) }, minLines = 3)
            }
        }
    }
}
