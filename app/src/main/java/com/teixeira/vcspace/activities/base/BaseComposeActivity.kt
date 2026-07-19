/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.activities.base

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.teixeira.vcspace.compose.LocalDarkMode
import com.teixeira.vcspace.core.settings.Settings.General.rememberFollowSystemTheme
import com.teixeira.vcspace.core.settings.Settings.General.rememberIsDarkMode
import com.teixeira.vcspace.ui.LocalToastHostState
import com.teixeira.vcspace.ui.ToastHost
import com.teixeira.vcspace.ui.rememberToastHostState
import com.teixeira.vcspace.ui.theme.VCSpaceTheme

abstract class BaseComposeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VCSpaceTheme {
                val systemBarFollowThemeState by rememberSaveable { mutableStateOf(true) }

                val followSystemTheme by rememberFollowSystemTheme()
                val localDarkTheme by rememberIsDarkMode()
                val systemDarkTheme = isSystemInDarkTheme()

                val darkTheme by remember(followSystemTheme, localDarkTheme, systemDarkTheme) {
                    mutableStateOf(if (followSystemTheme) systemDarkTheme else localDarkTheme)
                }

                LaunchedEffect(darkTheme, systemBarFollowThemeState) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        ) { darkTheme || !systemBarFollowThemeState },
                        navigationBarStyle = SystemBarStyle.auto(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        ) { darkTheme || !systemBarFollowThemeState }
                    )
                }

                ProvideBaseCompositionLocals(darkMode = darkTheme) {
                    MainScreen()
                    ToastHost()
                }
            }
        }
    }

    @Composable
    private fun ProvideBaseCompositionLocals(
        darkMode: Boolean,
        content: @Composable () -> Unit
    ) {
        val toastHostState = rememberToastHostState()

        CompositionLocalProvider(
            LocalLifecycleScope provides lifecycleScope,
            LocalLayoutInflater provides layoutInflater,
            LocalToastHostState provides toastHostState,
            LocalDarkMode provides darkMode,
            content = content
        )
    }

    @Composable
    protected abstract fun MainScreen()
}

@Composable
fun ObserveLifecycleEvents(onStateChanged: (Lifecycle.Event) -> Unit) {
    val currentOnStateChanged by rememberUpdatedState(onStateChanged)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            currentOnStateChanged(event)
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
