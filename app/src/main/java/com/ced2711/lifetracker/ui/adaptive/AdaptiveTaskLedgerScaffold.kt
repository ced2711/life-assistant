package com.ced2711.lifetracker.ui.adaptive

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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ced2711.lifetracker.domain.model.TopLevelDestination

private val NoInsets = WindowInsets(0, 0, 0, 0)
private val LocalAuxiliaryTitle = staticCompositionLocalOf<String?> { null }
private val TopLevelDestinations = TopLevelDestination.entries
private val CompactHeightThreshold = 320.dp
private val RailWidth = 80.dp
private val CompactRailWidth = 64.dp
private val MinimumSplitRailWidth = 48.dp
private val MinimumHorizontalChromeHeight = 96.dp
private val StatusBarVisualOverlap = 12.dp
private val MinimumStatusBarClearance = 24.dp

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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val safePaneLayout = calculateSafePaneLayout(
            availableWidth = maxWidth,
            availableHeight = maxHeight,
            foldingFeature = foldingFeature,
            density = LocalDensity.current,
        )

        CompositionLocalProvider(
            LocalSafePaneLayout provides safePaneLayout,
            LocalAuxiliaryTitle provides effectiveAuxiliaryTitle,
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
    val safeDrawingInsets = WindowInsets.safeDrawing
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
            includeIme = true,
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
        includeIme = navigationPane.touchesWindowBottom(safePaneLayout),
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
        includeIme = contentPane.touchesWindowBottom(safePaneLayout),
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
        includeIme = chromePane.touchesWindowBottom(safePaneLayout),
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
        includeIme = contentPane.touchesWindowBottom(safePaneLayout),
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
    includeIme: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val compactTopSafeDrawing = WindowInsets(
        left = safeDrawing.getLeft(density, layoutDirection),
        top = reducedStatusBarTopInset(
            safeTopPx = safeDrawing.getTop(density),
            desiredOverlapPx = with(density) { StatusBarVisualOverlap.roundToPx() },
            minimumClearancePx = with(density) { MinimumStatusBarClearance.roundToPx() },
        ),
        right = safeDrawing.getRight(density, layoutDirection),
        bottom = safeDrawing.getBottom(density),
    )
    Box(
        modifier = Modifier
            .offset(x = pane.left, y = pane.top)
            .width(pane.width)
            .height(pane.height)
            .clipToBounds()
            .windowInsetsPadding(
                if (includeIme) {
                    compactTopSafeDrawing.union(WindowInsets.ime)
                } else {
                    compactTopSafeDrawing
                },
            ),
    ) {
        content()
    }
}

internal fun reducedStatusBarTopInset(
    safeTopPx: Int,
    desiredOverlapPx: Int,
    minimumClearancePx: Int,
): Int {
    val safeTop = safeTopPx.coerceAtLeast(0)
    val minimumClearance = minimumClearancePx.coerceIn(0, safeTop)
    return (safeTop - desiredOverlapPx.coerceAtLeast(0)).coerceAtLeast(minimumClearance)
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

@Composable
private fun TaskLedgerNavigationBar(
    selected: TopLevelDestination,
    onSelected: (TopLevelDestination) -> Unit,
    isSettings: Boolean,
    compact: Boolean,
) {
    NavigationBar(
        modifier = Modifier.height(if (compact) 48.dp else 72.dp),
        windowInsets = NoInsets,
    ) {
        TopLevelDestinations.forEach { destination ->
            val localizedLabel = localizedText(destination.label)
            NavigationBarItem(
                selected = !isSettings && selected == destination,
                onClick = { onSelected(destination) },
                modifier = Modifier.semantics {
                    contentDescription = localizedLabel
                },
                icon = {
                    DestinationIcon(
                        destination = destination,
                        contentDescription = null,
                    )
                },
                label = if (compact) null else {
                    { Text(localizedLabel, maxLines = 1) }
                },
                alwaysShowLabel = !compact,
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
    NavigationRail(
        modifier = modifier,
        windowInsets = NoInsets,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopLevelDestinations.forEach { destination ->
                val localizedLabel = localizedText(destination.label)
                NavigationRailItem(
                selected = !isSettings && selected == destination,
                onClick = { onSelected(destination) },
                modifier = (if (compact) Modifier.height(48.dp) else Modifier).semantics {
                    contentDescription = localizedLabel
                },
                icon = {
                    DestinationIcon(
                        destination = destination,
                        contentDescription = null,
                    )
                },
                    label = if (compact) null else {
                        { Text(localizedLabel, maxLines = 1) }
                    },
                    alwaysShowLabel = !compact,
                )
            }
        }
    }
}

@Composable
private fun TaskLedgerTopBar(
    selected: TopLevelDestination,
    onSettings: () -> Unit,
    isSettings: Boolean,
    compact: Boolean,
) {
    val auxiliaryTitle = LocalAuxiliaryTitle.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 48.dp else 56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedText(auxiliaryTitle ?: if (isSettings) "Settings" else selected.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = if (isSettings) {
                        Icons.AutoMirrored.Filled.ArrowBack
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = localizedText(if (isSettings) "Back" else "Settings"),
                    tint = if (isSettings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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

private fun SafePaneBounds.touchesWindowBottom(layout: SafePaneLayout): Boolean =
    bottom >= layout.windowHeight

private val TopLevelDestination.label: String
    get() = when (this) {
        TopLevelDestination.TODO -> "Todo"
        TopLevelDestination.LEDGER -> "Ledger"
        TopLevelDestination.CALENDAR -> "Calendar"
        TopLevelDestination.NOTES -> "Notes"
    }

private val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.TODO -> Icons.Outlined.CheckCircle
        TopLevelDestination.LEDGER -> Icons.Outlined.AccountBalanceWallet
        TopLevelDestination.CALENDAR -> Icons.Outlined.CalendarMonth
        TopLevelDestination.NOTES -> Icons.Outlined.Description
    }
