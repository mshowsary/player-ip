package com.novaplay.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.theme.LocalNovaAccents
import com.novaplay.tv.ui.theme.isTvDevice

private const val CATEGORY_DIALOG_THRESHOLD = 8

/**
 * Compact catalogue navigation that stays fast even when a provider exposes
 * dozens or hundreds of categories. Small catalogues keep the familiar chip
 * strip; large catalogues keep only useful shortcuts and move the complete
 * list into a searchable, vertically scrolling picker.
 */
@Composable
fun CompactCategorySelector(
    categories: List<Pair<Long, String>>,
    selectedCategoryId: Long?,
    searchActive: Boolean,
    onSelectCategory: (Long?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!shouldUseCategoryPicker(categories.size)) {
        CategoryChipsRow(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            searchActive = searchActive,
            onSelectCategory = onSelectCategory,
            onOpenSearch = onOpenSearch,
            modifier = modifier,
        )
        return
    }

    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var restoreTriggerFocus by remember { mutableStateOf(false) }
    val pickerTriggerFocus = remember { FocusRequester() }
    val firstPickerRowFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val selectedProviderCategory = categories.firstOrNull { it.first == selectedCategoryId }

    LaunchedEffect(pickerOpen, restoreTriggerFocus, isTv) {
        if (!pickerOpen && restoreTriggerFocus && isTv) {
            runCatching { pickerTriggerFocus.requestFocus() }
            restoreTriggerFocus = false
        }
    }

    val closePicker = {
        pickerOpen = false
        categoryQuery = ""
        restoreTriggerFocus = true
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item(key = "search") {
            CompactCategoryChip(
                label = "Search",
                selected = searchActive,
                onClick = onOpenSearch,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        item(key = "all") {
            CompactCategoryChip(
                label = "All",
                selected = !searchActive && selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
            )
        }
        item(key = "bookmarks") {
            CompactCategoryChip(
                label = "Bookmarks",
                selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS,
                onClick = { onSelectCategory(ContentRepository.CATEGORY_BOOKMARKS) },
            )
        }
        item(key = "recent") {
            CompactCategoryChip(
                label = "Recent",
                selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_RECENT,
                onClick = { onSelectCategory(ContentRepository.CATEGORY_RECENT) },
            )
        }
        item(key = "provider-categories") {
            CompactCategoryChip(
                label = selectedProviderCategory?.second ?: "Categories",
                selected = !searchActive && selectedProviderCategory != null,
                onClick = { pickerOpen = true },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                },
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .focusRequester(pickerTriggerFocus),
            )
        }
    }

    if (pickerOpen) {
        val filteredCategories = remember(categories, categoryQuery) {
            filterProviderCategories(categories, categoryQuery)
        }

        LaunchedEffect(isTv, filteredCategories.isNotEmpty()) {
            if (isTv && filteredCategories.isNotEmpty()) {
                runCatching { firstPickerRowFocus.requestFocus() }
            }
        }

        NovaDialog(
            title = "Choose category",
            onDismiss = closePicker,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryFilterField(
                    query = categoryQuery,
                    onQueryChange = { categoryQuery = it },
                )
                Text(
                    text = "${filteredCategories.size} of ${categories.size} categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No category matches your search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp),
                    ) {
                        items(
                            count = filteredCategories.size,
                            key = { filteredCategories[it].first },
                        ) { index ->
                            val (id, name) = filteredCategories[index]
                            CategoryPickerRow(
                                name = name,
                                selected = id == selectedCategoryId,
                                onClick = {
                                    onSelectCategory(id)
                                    closePicker()
                                },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstPickerRowFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rounded pill chip for the compact strip. Selection is exposed to accessibility
 * services and keyboard users while the visual focus treatment remains unchanged.
 */
@Composable
private fun CompactCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .semantics { this.selected = selected }
            .then(
                if (selected) {
                    Modifier.border(1.dp, LocalNovaAccents.current.gradient, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(50),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.04f,
        accessibilityLabel = label,
        accessibilityRole = Role.RadioButton,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 15.dp, vertical = 10.dp),
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailingIcon?.invoke()
        }
    }
}

/**
 * Inline filter box inside the category picker dialog; narrows the list as
 * the user types (plain substring match, not FTS).
 */
@Composable
private fun CategoryFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isBlank()) {
                        Text(
                            text = "Filter categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Single focusable row in the category picker; the selected entry carries a
 * primary indicator bar. OK/click selects it and closes the dialog.
 */
@Composable
private fun CategoryPickerRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { this.selected = selected },
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.02f,
        accessibilityLabel = name,
        accessibilityRole = Role.RadioButton,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 22.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** True when the catalogue is large enough to warrant the searchable picker dialog. */
internal fun shouldUseCategoryPicker(categoryCount: Int): Boolean =
    categoryCount > CATEGORY_DIALOG_THRESHOLD

/** Case-insensitive substring filter over provider categories; a blank query keeps them all. */
internal fun filterProviderCategories(
    categories: List<Pair<Long, String>>,
    rawQuery: String,
): List<Pair<Long, String>> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return categories
    return categories.filter { (_, name) -> name.contains(query, ignoreCase = true) }
}
