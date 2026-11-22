package app.gamenative.wow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.screen.wow.MaliSettings
import app.gamenative.wow.GraphicsDrivers
import app.gamenative.wow.MaliPanVk
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Mali build: Settings > Mali graphics, in place of Graphics driver (whose Turnip builds don't run on Mali).
 * The driver (the device's own, or PanVK, experimental) and the stock driver's options (MaliSupport.kt).
 */
@Composable
internal fun MaliGraphicsSection(@Suppress("UNUSED_PARAMETER") launcher: LauncherController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var drivers by remember { mutableStateOf(GraphicsDrivers.list(context).filter { it.added }) }
    var current by remember { mutableStateOf(GraphicsDrivers.chosen(context)?.takeIf { name -> drivers.any { it.name == name } }) }
    var options by remember { mutableStateOf(MaliSettings.options(context)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    FwSection(stringResource(R.string.mali_graphics)) {
        Text(stringResource(R.string.mali_driver_explanation), style = Fw.Secondary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FwRow(selected = current == null, onClick = {
                GraphicsDrivers.choose(context, null)
                current = null
            }) {
                Text(stringResource(R.string.mali_driver_stock), style = Fw.Text, modifier = Modifier.weight(1f))
                if (current == null) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
            }
            drivers.forEach { d ->
                FwRow(selected = d.name == current, onClick = {
                    GraphicsDrivers.choose(context, d.name)
                    current = d.name
                }) {
                    Text(
                        if (d.name == MaliPanVk.DRIVER_NAME) stringResource(R.string.mali_driver_panvk) else d.name,
                        style = Fw.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (d.version.isNotEmpty()) Text(d.version.take(40), style = Fw.Data.copy(color = Fw.Ash))
                    if (d.name == current) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
        if (drivers.none { it.name == MaliPanVk.DRIVER_NAME }) {
            Text(stringResource(R.string.mali_panvk_explanation), style = Fw.Secondary)
            val downloading = progress
            FwButton(
                if (downloading == null) stringResource(R.string.mali_panvk_download)
                else stringResource(R.string.mali_panvk_downloading, (downloading * 100).toInt()),
                {
                    message = null
                    progress = 0f
                    scope.launch {
                        val result = runCatching { MaliPanVk.install(context) { p -> progress = p } }
                        progress = null
                        result.onSuccess { name ->
                            drivers = GraphicsDrivers.list(context).filter { it.added }
                            GraphicsDrivers.choose(context, name)
                            current = name
                        }.onFailure {
                            Timber.w(it, "PanVK install failed")
                            message = context.getString(R.string.mali_panvk_failed, it.message.orEmpty())
                        }
                    }
                },
                kind = FwButtonKind.Ghost,
                enabled = downloading == null,
            )
        } else if (current != null) {
            Text(stringResource(R.string.mali_panvk_selected_note), style = Fw.Secondary.copy(color = Fw.Warn))
        }
        drivers.firstOrNull { it.name == current }?.let { picked ->
            FwButton(stringResource(R.string.fw_remove), {
                scope.launch {
                    withContext(Dispatchers.IO) { GraphicsDrivers.remove(context, picked.name) }
                    drivers = GraphicsDrivers.list(context).filter { it.added }
                    current = null
                }
            }, kind = FwButtonKind.Ghost)
        }
        message?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
    }

    FwSection(stringResource(R.string.mali_tuning)) {
        Text(stringResource(R.string.mali_tuning_explanation), style = Fw.Secondary)
        MaliToggle(
            title = stringResource(R.string.mali_low_latency),
            detail = stringResource(R.string.mali_low_latency_explanation),
            on = options.lowLatency,
        ) {
            MaliSettings.setLowLatency(context, it)
            options = MaliSettings.options(context)
        }
        MaliToggle(
            title = stringResource(R.string.mali_light_textures),
            detail = stringResource(R.string.mali_light_textures_explanation),
            on = options.lightTextures,
        ) {
            MaliSettings.setLightTextures(context, it)
            options = MaliSettings.options(context)
        }
    }
}

@Composable
private fun MaliToggle(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = Fw.Text)
        Text(detail, style = Fw.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to stringResource(R.string.fw_on), false to stringResource(R.string.fw_off)).forEach { (value, label) ->
                FwRow(selected = on == value, onClick = { onChange(value) }, modifier = Modifier.weight(1f)) {
                    Text(label, style = Fw.Text, modifier = Modifier.weight(1f))
                    if (on == value) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
    }
}
