package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import com.ced2711.lifetracker.ui.theme.LifeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import kotlin.math.roundToInt
import com.ced2711.lifetracker.domain.model.TopLevelDestination

private val NoInsets = WindowInsets(0, 0, 0, 0)
private val LocalAuxiliaryTitle = staticCompositionLocalOf<String?> { null }
// The modules the user chose to show, in navigation order.
private val LocalNavigationDestinations = staticCompositionLocalOf<List<TopLevelDestination>> { TopLevelDestination.entries }
private val CompactHeightThreshold = 320.dp
private val RailWidth = 80.dp
private val CompactRailWidth = 64.dp
private val MinimumSplitRailWidth = 48.dp
private val MinimumHorizontalChromeHeight = 96.dp

/**
 * Responsive root scaffold for TaskLedger.
 *
 * System bars, the IME, vertical hinges, and horizontal hinges are all treated as layout inputs.
 * When both foldable regions are usable, navigation chrome occupies the secondary pane while
 * feature content gets the larger primary pane. Otherwise the complete scaffold remains inside the
 * largest safe pane. [LocalSafePaneLayout] exposes both regions to descendants and dialogs.
 */
@Composable
fun AdaptiveTaskLedgerScaffold(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    auxiliaryTitle: String? = null,
    modifier: Modifier = Modifier,
    foldingFeature: FoldingFeature? = null,
    destinations: List<TopLevelDestination> = TopLevelDestination.entries,
    syncStatus: TopBarSyncStatus? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val effectiveAuxiliaryTitle = if (isSettings) auxiliaryTitle ?: "Settings" else null
    val contentStateHolder = rememberSaveableStateHolder()
    val contentStateKey = if (isSettings) {
        "auxiliary:${effectiveAuxiliaryTitle.orEmpty()}"
    } else {
        "destination:${selected.name}"
    }
    val latestContent by rememberUpdatedState(content)
    val latestContentStateKey by rememberUpdatedState(contentStateKey)
    val movableFeatureContent = remember(contentStateHolder) {
        movableContentOf<PaddingValues> { paddingValues ->
            contentStateHolder.SaveableStateProvider(latestContentStateKey) {
                latestContent(paddingValues)
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        val density = LocalDensity.current
        val geometricPaneLayout = calculateSafePaneLayout(
            availableWidth = maxWidth,
            availableHeight = maxHeight,
            foldingFeature = foldingFeature,
            density = density,
        )
        val safePaneLayout = keyboardAwarePaneLayout(
            geometricPaneLayout,
            with(density) { appImeInsets().getBottom(density).toDp() },
        )

        CompositionLocalProvider(
            LocalSafePaneLayout provides safePaneLayout,
            LocalAuxiliaryTitle provides effectiveAuxiliaryTitle,
            LocalNavigationDestinations provides destinations,
            LocalTopBarSyncStatus provides syncStatus,
        ) {
            FoldAwareScaffold(
                safePaneLayout = safePaneLayout,
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                content = movableFeatureContent,
            )
        }
    }
}

@Composable
private fun FoldAwareScaffold(
    safePaneLayout: SafePaneLayout,
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val secondaryPane = safePaneLayout.secondaryPane
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingInsets = paneSafeDrawingInsets(
        secondaryPane ?: safePaneLayout.primaryPane,
        safePaneLayout.windowWidth,
        safePaneLayout.windowHeight,
    )
    val horizontalSafeDrawing = with(density) {
        (
            safeDrawingInsets.getLeft(density, layoutDirection) +
                safeDrawingInsets.getRight(density, layoutDirection)
            ).toDp()
    }
    val verticalSafeDrawing = with(density) {
        (safeDrawingInsets.getTop(density) + safeDrawingInsets.getBottom(density)).toDp()
    }
    when {
        secondaryPane != null &&
            safePaneLayout.splitAxis == SafePaneAxis.VERTICAL &&
            usablePaneExtent(secondaryPane.width, horizontalSafeDrawing) >= MinimumSplitRailWidth -> {
            VerticalFoldScaffold(
                safePaneLayout = safePaneLayout,
                navigationPane = secondaryPane,
                contentPane = safePaneLayout.primaryPane,
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                content = content,
            )
        }

        secondaryPane != null &&
            safePaneLayout.splitAxis == SafePaneAxis.HORIZONTAL &&
            usablePaneExtent(secondaryPane.height, verticalSafeDrawing) >= MinimumHorizontalChromeHeight -> {
            HorizontalFoldScaffold(
                safePaneLayout = safePaneLayout,
                chromePane = secondaryPane,
                contentPane = safePaneLayout.primaryPane,
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                content = content,
            )
        }

        else -> PaneHost(
            pane = safePaneLayout.primaryPane,
            // The standard layout's header sits at the top of this pane and avoids a camera itself.
            allowTopCutoutIsland = true,
        ) {
            StandardAdaptiveScaffold(
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                content = content,
            )
        }
    }
}

@Composable
private fun StandardAdaptiveScaffold(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactChrome = maxHeight < CompactHeightThreshold
        if (maxWidth >= 600.dp) {
            ExpandedScaffold(
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                compactChrome = compactChrome,
                content = content,
            )
        } else {
            CompactScaffold(
                selected = selected,
                onSelected = onSelected,
                onSettings = onSettings,
                isSettings = isSettings,
                compactChrome = compactChrome,
                content = content,
            )
        }
    }
}

@Composable
private fun VerticalFoldScaffold(
    safePaneLayout: SafePaneLayout,
    navigationPane: SafePaneBounds,
    contentPane: SafePaneBounds,
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val compactChrome =
        minOf(navigationPane.height, contentPane.height) < CompactHeightThreshold ||
            navigationPane.width < RailWidth
    PaneHost(
        pane = navigationPane,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val navigationWidth = (if (compactChrome) CompactRailWidth else RailWidth)
                    .coerceAtMost(maxWidth)
                val navigationAlignment = if (navigationPane.right <= contentPane.left) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
                TaskLedgerNavigationRail(
                    selected = selected,
                    onSelected = onSelected,
                    isSettings = isSettings,
                    compact = compactChrome,
                    modifier = Modifier
                        .align(navigationAlignment)
                        .width(navigationWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }

    PaneHost(
        pane = contentPane,
    ) {
        Scaffold(
            contentWindowInsets = NoInsets,
            topBar = {
                TaskLedgerTopBar(
                    selected = selected,
                    onSettings = onSettings,
                    isSettings = isSettings,
                    compact = compactChrome,
                )
            },
            content = content,
        )
    }
}

@Composable
private fun HorizontalFoldScaffold(
    safePaneLayout: SafePaneLayout,
    chromePane: SafePaneBounds,
    contentPane: SafePaneBounds,
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val compactChrome = chromePane.height < 160.dp || contentPane.height < CompactHeightThreshold
    val chromeIsAboveContent = chromePane.bottom <= contentPane.top

    PaneHost(
        pane = chromePane,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (chromeIsAboveContent) Spacer(Modifier.weight(1f))
                if (!chromeIsAboveContent) {
                    TaskLedgerNavigationBar(
                        selected = selected,
                        onSelected = onSelected,
                        isSettings = isSettings,
                        compact = compactChrome,
                    )
                }
                TaskLedgerTopBar(
                    selected = selected,
                    onSettings = onSettings,
                    isSettings = isSettings,
                    compact = compactChrome,
                )
                if (chromeIsAboveContent) {
                    TaskLedgerNavigationBar(
                        selected = selected,
                        onSelected = onSelected,
                        isSettings = isSettings,
                        compact = compactChrome,
                    )
                }
                if (!chromeIsAboveContent) Spacer(Modifier.weight(1f))
            }
        }
    }

    PaneHost(
        pane = contentPane,
    ) {
        Scaffold(
            contentWindowInsets = NoInsets,
            content = content,
        )
    }
}

@Composable
private fun PaneHost(
    pane: SafePaneBounds,
    allowTopCutoutIsland: Boolean = false,
    content: @Composable () -> Unit,
) {
    val layout = requireNotNull(LocalSafePaneLayout.current)
    Box(
        modifier = Modifier
            .offset(x = pane.left, y = pane.top)
            .width(pane.width)
            .height(pane.height)
            .clipToBounds()
            // Paint behind the camera safety area with the same surface as the app header.
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .paneSafeDrawingPadding(pane, layout.windowWidth, layout.windowHeight, allowTopCutoutIsland),
    ) {
        val island = if (allowTopCutoutIsland) topCutoutIsland() else null
        CompositionLocalProvider(
            LocalHeaderCutoutIsland provides island?.let { CutoutIsland(it.left, it.top, it.right, it.bottom) },
        ) {
            content()
        }
    }
}

@Composable
private fun CompactScaffold(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    compactChrome: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TaskLedgerTopBar(
                selected = selected,
                onSettings = onSettings,
                isSettings = isSettings,
                compact = compactChrome,
            )
        },
        bottomBar = {
            TaskLedgerNavigationBar(
                selected = selected,
                onSelected = onSelected,
                isSettings = isSettings,
                compact = compactChrome,
            )
        },
        content = content,
    )
}

@Composable
private fun ExpandedScaffold(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    onSettings: () -> Unit,
    isSettings: Boolean,
    compactChrome: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        TaskLedgerNavigationRail(
            selected = selected,
            onSelected = onSelected,
            isSettings = isSettings,
            compact = compactChrome,
            modifier = Modifier
                .width(if (compactChrome) CompactRailWidth else RailWidth)
                .fillMaxHeight(),
        )

        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = NoInsets,
            topBar = {
                TaskLedgerTopBar(
                    selected = selected,
                    onSettings = onSettings,
                    isSettings = isSettings,
                    compact = compactChrome,
                )
            },
            content = content,
        )
    }
}

/**
 * Bottom navigation: a flat bar with a hairline on top. The selected module's icon sits on a
 * soft accent pill; labels show when there is room (five modules or fewer), otherwise only the
 * selected one is named.
 */
@Composable
private fun TaskLedgerNavigationBar(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    isSettings: Boolean,
    compact: Boolean,
) {
    val destinations = LocalNavigationDestinations.current
    // More than five items do not fit readable labels on a phone; name only the selected one then.
    val crowded = destinations.size > 5
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LifeTheme.colors.divider))
            Row(
                Modifier.fillMaxWidth().height(if (compact) 47.dp else 71.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val isSelected = !isSettings && selected == destination
                    NavigationEntry(
                        destination = destination,
                        selected = isSelected,
                        showLabel = !compact && (!crowded || isSelected),
                        onClick = { onSelected(destination) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationEntry(
    destination: TopLevelDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localizedLabel = localizedText(destination.label)
    val pill by animateColorAsState(if (selected) LifeTheme.colors.accentSoft else Color.Transparent, label = "navigation-pill")
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = localizedLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(CircleShape).background(pill).padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, null, Modifier.size(22.dp), tint = tint)
        }
        if (showLabel) {
            Text(
                localizedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun TaskLedgerNavigationRail(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    isSettings: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val island = LocalHeaderCutoutIsland.current
    var railTopClearance by remember { mutableIntStateOf(0) }
    Surface(
        modifier = if (island == null) modifier else modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val overlaps = island.left < bounds.right && island.right > bounds.left && island.top < bounds.bottom
            railTopClearance = if (overlaps) (island.bottom - bounds.top.roundToInt()).coerceAtLeast(0) else 0
        },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = with(LocalDensity.current) { railTopClearance.toDp() })
                .padding(vertical = if (compact) 4.dp else 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp),
        ) {
            LocalNavigationDestinations.current.forEach { destination ->
                NavigationEntry(
                    destination = destination,
                    selected = !isSettings && selected == destination,
                    showLabel = !compact,
                    onClick = { onSelected(destination) },
                    modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 60.dp),
                )
            }
        }
    }
}

/** The page's name with the sync status and the way to Settings (or back from it). */
@Composable
private fun TaskLedgerTopBar(
    selected: TopLevelDestination,
    onSettings: () -> Unit,
    isSettings: Boolean,
    compact: Boolean,
) {
    val auxiliaryTitle = LocalAuxiliaryTitle.current
    val syncStatus = LocalTopBarSyncStatus.current
    val island = LocalHeaderCutoutIsland.current
    val density = LocalDensity.current
    var barBounds by remember { mutableStateOf<CutoutIsland?>(null) }
    val cutoutPadding = barBounds?.let { bounds ->
        with(density) {
            headerCutoutPadding(
                barLeft = bounds.left,
                barTop = bounds.top,
                barRight = bounds.right,
                barBottom = bounds.bottom,
                titleStart = 20.dp.roundToPx(),
                trailingWidth = (if (syncStatus == null) 52.dp else 100.dp).roundToPx(),
                minimumTitleWidth = 48.dp.roundToPx(),
                gap = 8.dp.roundToPx(),
                island = island,
            )
        }
    } ?: HeaderCutoutPadding()
    Surface(
        modifier = if (island == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            barBounds = CutoutIsland(
                bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
            )
        },
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 48.dp else 60.dp)
                .padding(start = if (isSettings) 4.dp else 20.dp, end = 4.dp)
                .padding(
                    start = with(density) { cutoutPadding.start.toDp() },
                    end = with(density) { cutoutPadding.end.toDp() },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSettings) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("Back"), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(
                text = localizedText(auxiliaryTitle ?: if (isSettings) "Settings" else selected.label),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = with(density) { cutoutPadding.titleEnd.toDp() })
                    .semantics { heading() },
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            syncStatus?.let { CloudSyncStatusButton(it) }
            if (!isSettings) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, localizedText("Settings"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    destination: TopLevelDestination,
    contentDescription: String?,
) {
    Icon(
        imageVector = destination.icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(24.dp),
    )
}

private fun calculateSafePaneLayout(
    availableWidth: Dp,
    availableHeight: Dp,
    foldingFeature: FoldingFeature?,
    density: Density,
): SafePaneLayout {
    if (foldingFeature == null || !foldingFeature.isSeparating) {
        return SafePaneLayout.singlePane(availableWidth, availableHeight)
    }

    val splitAxis = when (foldingFeature.orientation) {
        FoldingFeature.Orientation.VERTICAL -> SafePaneAxis.VERTICAL
        FoldingFeature.Orientation.HORIZONTAL -> SafePaneAxis.HORIZONTAL
        else -> return SafePaneLayout.singlePane(availableWidth, availableHeight)
    }
    val window = with(density) {
        PixelPaneBounds(
            left = 0,
            top = 0,
            right = availableWidth.roundToPx(),
            bottom = availableHeight.roundToPx(),
        )
    }
    val featureBounds = foldingFeature.bounds
    val calculation = calculateSafeRegions(
        window = window,
        separatingFeature = PixelPaneBounds(
            left = featureBounds.left,
            top = featureBounds.top,
            right = featureBounds.right,
            bottom = featureBounds.bottom,
        ),
        splitAxis = splitAxis,
    )

    return SafePaneLayout(
        windowWidth = availableWidth,
        windowHeight = availableHeight,
        primaryPane = calculation.primary.toDpBounds(density),
        secondaryPane = calculation.secondary?.toDpBounds(density),
        separatingFeatureBounds = calculation.separator?.toDpBounds(density),
        splitAxis = calculation.splitAxis,
    )
}

private fun PixelPaneBounds.toDpBounds(density: Density): SafePaneBounds = with(density) {
    SafePaneBounds(
        left = left.toDp(),
        top = top.toDp(),
        right = right.toDp(),
        bottom = bottom.toDp(),
    )
}

internal val TopLevelDestination.label: String
    get() = when (this) {
        TopLevelDestination.TODAY -> "Today"
        TopLevelDestination.TODO -> "Todo"
        TopLevelDestination.LEDGER -> "Ledger"
        TopLevelDestination.CALENDAR -> "Calendar"
        TopLevelDestination.NOTES -> "Notes"
        TopLevelDestination.DIARY -> "Diary"
        TopLevelDestination.CONFESSIONAL -> "Confessional"
    }

internal val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.TODAY -> Icons.Outlined.WbSunny
        TopLevelDestination.TODO -> Icons.Outlined.CheckCircle
        TopLevelDestination.LEDGER -> Icons.Outlined.AccountBalanceWallet
        TopLevelDestination.CALENDAR -> Icons.Outlined.CalendarMonth
        TopLevelDestination.NOTES -> Icons.Outlined.Description
        TopLevelDestination.DIARY -> Icons.Outlined.Book
        TopLevelDestination.CONFESSIONAL -> Icons.Outlined.LocalFireDepartment
    }
