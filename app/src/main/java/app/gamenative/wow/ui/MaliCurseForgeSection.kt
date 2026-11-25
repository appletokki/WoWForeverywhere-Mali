package app.gamenative.wow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.wow.addons.provider.MaliCurseForge
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.theme.Fw
import kotlinx.coroutines.launch
import timber.log.Timber

/** Mali build: Settings > CurseForge search, the player's own API key. */
@Composable
internal fun MaliCurseForgeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(MaliCurseForge.key(context).isNotBlank()) }
    var typed by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }

    FwSection(stringResource(R.string.mali_curseforge)) {
        if (saved) {
            Text(stringResource(R.string.mali_curseforge_saved), style = Fw.Text.copy(color = Fw.Ok))
            FwButton(stringResource(R.string.fw_remove), {
                MaliCurseForge.clear(context)
                saved = false
            }, kind = FwButtonKind.Ghost)
        } else {
            Text(stringResource(R.string.mali_curseforge_explanation), style = Fw.Secondary)
            FwButton(stringResource(R.string.mali_curseforge_get_key), { Links.open(context, MaliCurseForge.CONSOLE_URL) }, kind = FwButtonKind.Ghost)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    typed,
                    {
                        typed = it
                        problem = null
                    },
                    Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.mali_curseforge_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = Fw.Text,
                )
                val key = MaliCurseForge.clean(typed)
                FwButton(stringResource(if (checking) R.string.fw_checking else R.string.fw_save), {
                    if (key != null) {
                        checking = true
                        problem = null
                        scope.launch {
                            when (MaliCurseForge.check(key)) {
                                MaliCurseForge.Check.OK -> runCatching { MaliCurseForge.save(context, key) }
                                    .onSuccess {
                                        typed = ""
                                        saved = true
                                    }
                                    .onFailure {
                                        Timber.w("Saving the CurseForge key failed (${it.javaClass.simpleName})")
                                        problem = R.string.mali_curseforge_not_saved
                                    }
                                MaliCurseForge.Check.REJECTED -> problem = R.string.mali_curseforge_rejected
                                MaliCurseForge.Check.FAILED -> problem = R.string.mali_curseforge_unreachable
                            }
                            checking = false
                        }
                    }
                }, enabled = key != null && !checking)
            }
            problem?.let { Text(stringResource(it), style = Fw.Secondary.copy(color = Fw.Warn)) }
        }
    }
}
