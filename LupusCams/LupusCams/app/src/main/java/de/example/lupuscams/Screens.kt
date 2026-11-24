package de.example.lupuscams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private sealed interface Screen {
    data object Grid : Screen
    data class Full(val index: Int) : Screen
    data class Edit(val index: Int) : Screen
}

@Composable
fun CamApp(store: CameraStore) {
    var cameras by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Grid) }

    BackHandler(enabled = screen != Screen.Grid) { screen = Screen.Grid }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.Grid -> GridScreen(
                    cameras = cameras,
                    onOpen = { i ->
                        screen = if (cameras[i].host.isBlank()) Screen.Edit(i) else Screen.Full(i)
                    },
                )

                is Screen.Full -> FullScreen(
                    camera = cameras[s.index],
                    onBack = { screen = Screen.Grid },
                    onEdit = { screen = Screen.Edit(s.index) },
                )

                is Screen.Edit -> EditScreen(
                    camera = cameras[s.index],
                    onCancel = { screen = Screen.Grid },
                    onSave = { updated ->
                        cameras = cameras.toMutableList().also { it[s.index] = updated }
                        store.save(cameras)
                        screen = Screen.Grid
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridScreen(cameras: List<Camera>, onOpen: (Int) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Kameras") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(cameras) { index, cam ->
                val uri = cam.rtspUri(main = false)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable { onOpen(index) },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        if (uri != null) {
                            CameraPlayer(uri = uri, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(
                                "Nicht eingerichtet\nTippen zum Einrichten",
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                            )
                        }
                        Text(
                            cam.name,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0x99000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreen(camera: Camera, onBack: () -> Unit, onEdit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val uri = camera.rtspUri(main = true)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (uri != null) CameraPlayer(uri = uri, modifier = Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Zurück") }
            Text(camera.name, color = Color.White)
            TextButton(onClick = onEdit) { Text("Bearbeiten") }
        }
    }
}

@Composable
private fun EditScreen(camera: Camera, onCancel: () -> Unit, onSave: (Camera) -> Unit) {
    var name by remember { mutableStateOf(camera.name) }
    var host by remember { mutableStateOf(camera.host) }
    var port by remember { mutableStateOf(camera.port.toString()) }
    var user by remember { mutableStateOf(camera.user) }
    var password by remember { mutableStateOf(camera.password) }
    var path by remember { mutableStateOf(camera.pathTemplate) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kamera einrichten", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            host, { host = it },
            label = { Text("IP-Adresse im Heimnetz (z. B. 192.168.178.50)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            port, { port = it.filter(Char::isDigit) },
            label = { Text("RTSP-Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(user, { user = it }, label = { Text("Benutzer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            password, { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            path, { path = it },
            label = { Text("RTSP-Pfad (%d = 0 Haupt-, 1 Substream)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                onSave(
                    Camera(
                        name = name.trim().ifBlank { camera.name },
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 554,
                        user = user.trim(),
                        password = password,
                        pathTemplate = path.trim().ifBlank { Camera.DEFAULT_PATH },
                    )
                )
            }) { Text("Speichern") }
            TextButton(onClick = onCancel) { Text("Abbrechen") }
        }
    }
}
