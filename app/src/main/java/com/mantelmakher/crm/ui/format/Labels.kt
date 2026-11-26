package com.mantelmakher.crm.ui.format

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.mantelmakher.crm.R
import com.mantelmakher.crm.domain.logic.DateTimes
import com.mantelmakher.crm.domain.logic.MoneyText
import com.mantelmakher.crm.domain.logic.Numbers
import com.mantelmakher.crm.domain.model.ActivityKind
import com.mantelmakher.crm.domain.model.ClientKind
import com.mantelmakher.crm.domain.model.DealStage
import com.mantelmakher.crm.domain.model.ListingPurpose
import com.mantelmakher.crm.domain.model.PropertyStatus
import com.mantelmakher.crm.domain.model.PropertyType
import com.mantelmakher.crm.domain.model.TaskFilter
import java.util.Locale

@StringRes
fun ClientKind.labelRes(): Int = when (this) {
    ClientKind.BUYER -> R.string.kind_buyer
    ClientKind.SELLER -> R.string.kind_seller
    ClientKind.TENANT -> R.string.kind_tenant
    ClientKind.LANDLORD -> R.string.kind_landlord
    ClientKind.OTHER -> R.string.kind_other
}

@StringRes
fun ListingPurpose.labelRes(): Int = when (this) {
    ListingPurpose.SALE -> R.string.purpose_sale
    ListingPurpose.RENT -> R.string.purpose_rent
}

@StringRes
fun PropertyType.labelRes(): Int = when (this) {
    PropertyType.APARTMENT -> R.string.type_apartment
    PropertyType.HOUSE -> R.string.type_house
    PropertyType.TOWNHOUSE -> R.string.type_townhouse
    PropertyType.COMMERCIAL -> R.string.type_commercial
    PropertyType.LAND -> R.string.type_land
    PropertyType.OTHER -> R.string.type_other
}

@StringRes
fun PropertyStatus.labelRes(): Int = when (this) {
    PropertyStatus.ACTIVE -> R.string.status_active
    PropertyStatus.RESERVED -> R.string.status_reserved
    PropertyStatus.SOLD -> R.string.status_sold
    PropertyStatus.RENTED -> R.string.status_rented
    PropertyStatus.ARCHIVED -> R.string.status_archived
}

@StringRes
fun DealStage.labelRes(): Int = when (this) {
    DealStage.LEAD -> R.string.stage_lead
    DealStage.SHOWING -> R.string.stage_showing
    DealStage.NEGOTIATION -> R.string.stage_negotiation
    DealStage.CONTRACT -> R.string.stage_contract
    DealStage.WON -> R.string.stage_won
    DealStage.LOST -> R.string.stage_lost
}

@StringRes
fun ActivityKind.labelRes(): Int = when (this) {
    ActivityKind.NOTE -> R.string.kind_note
    ActivityKind.CALL -> R.string.kind_call
    ActivityKind.MESSAGE -> R.string.kind_message
    ActivityKind.MEETING -> R.string.kind_meeting
    ActivityKind.VIEWING -> R.string.kind_viewing
}

@StringRes
fun TaskFilter.labelRes(): Int = when (this) {
    TaskFilter.ALL -> R.string.task_all
    TaskFilter.OPEN -> R.string.task_open
    TaskFilter.DONE -> R.string.task_done
    TaskFilter.TODAY -> R.string.task_today
    TaskFilter.OVERDUE -> R.string.task_overdue
}

@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun moneyLabel(amount: Long, currency: String, rent: Boolean = false): String {
    val formatted = MoneyText.format(amount, currency, appLocale())
        ?: return stringResource(R.string.price_on_request)
    return if (rent) formatted + stringResource(R.string.price_per_month) else formatted
}

@Composable
fun dateTimeLabel(epoch: Long?): String {
    if (epoch == null) return stringResource(R.string.no_due)
    return DateTimes.format(epoch, appLocale())
}

@Composable
fun decimalLabel(value: Double): String = Numbers.formatDecimal(value, appLocale())

fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

fun Context.dial(url: String): Boolean = tryStart(Intent(Intent.ACTION_DIAL, Uri.parse(url)))

fun Context.openHttps(url: String): Boolean = tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun Context.email(url: String): Boolean = tryStart(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
