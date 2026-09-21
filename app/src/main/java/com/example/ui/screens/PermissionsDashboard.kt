package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.AlyaViewModel

/**
 * PermissionsDashboard
 *
 * Delegated wrapper to CapabilityDashboardScreen.
 */
@Composable
fun PermissionsDashboard(
    viewModel: AlyaViewModel,
    onClose: () -> Unit = {},
    onOpenWakeUpActivation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CapabilityDashboardScreen(
        viewModel = viewModel,
        onClose = onClose,
        onOpenWakeUpActivation = onOpenWakeUpActivation,
        modifier = modifier
    )
}
