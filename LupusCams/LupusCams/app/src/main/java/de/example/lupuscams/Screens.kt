package de.example.lupuscams

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Grid : Screen
    data class Full(val id: String) : Screen
    data class Edit(val camera: Camera, val isNew: Boolean) : Screen
    data object Scan : Screen
}

private val PER_PAGE_OPTIONS = listOf(1, 2, 4, 6, 9)

@Composable
fun CamApp(store: CameraStore) {
    var cameras by remember { mutableStateOf(store.load()) }
    var perPage by remember { mutableIntStateOf(store.loadPerPage()) }
    var page by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Grid) }

    fun persist(list: List<Camera>) {
        cameras = list
        store.save(list)
    }

    BackHandler(enabled = screen != Screen.Grid) { screen = Screen.Grid }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.Grid -> GridScreen(
                    cameras = cameras,
                    perPage = perPage,
                    page = page,
                    onPage = { page = it },
                    onOpen = { cam ->
                        screen = if (cam.host.isBlank()) Screen.Edit(cam, false) else Screen.Full(cam.id)
                    },
                    onAdd = { screen = Screen.Edit(Camera(name = "Kamera ${cameras.size + 1}"), true) },
                    onScan = { screen = Screen.Scan },
                    onPerPage = {
                        perPage = it
                        page = 0
                        store.savePerPage(it)
                    },
                )

                is Screen.Full -> {
                    val cam = cameras.firstOrNull { it.id == s.id }
                    if (cam == null) {
                        screen = Screen.Grid
                    } else {
                        FullScreen(
                            camera = cam,
                            onBack = { screen = Screen.Grid },
                            onEdit = { screen = Screen.Edit(cam, false) },
                        )
                    }
                }

                is Screen.Edit -> EditScreen(
                    camera = s.camera,
                    isNew = s.isNew,
                    onCancel = { screen = Screen.Grid },
                    onSave = { updated ->
                        val list = if (s.isNew) {
                            cameras + updated
                        } else {
                            cameras.map { if (it.id == updated.id) updated else it }
                        }
                        persist(list)
                        screen = Screen.Grid
                    },
                    onDelete = {
                        persist(cameras.filter { it.id != s.camera.id })
                        screen = Screen.Grid
                    },
                )

                Screen.Scan -> ScanScreen(
                    existingHosts = cameras.map { it.host.trim() },
                    onBack = { screen = Screen.Grid },
                    onAdd = { ip ->
                        screen = Screen.Edit(
                            Camera(name = "Kamera ${ip.substringAfterLast('.')}", host = ip),
                            true,
                        )
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Übersicht mit Seiten

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GridScreen(
    cameras: List<Camera>,
    perPage: Int,
    page: Int,
    onPage: (Int) -> Unit,
    onOpen: (Camera) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onPerPage: (Int) -> Unit,
) {
    val pageCount = maxOf(1, (cameras.size + perPage - 1) / perPage)
    val pagerState = rememberPagerState(
        initialPage = page.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )
    val scope = rememberCoroutineScope()
    var showView by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { onPage(pagerState.currentPage) }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) pagerState.scrollToPage(pageCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kameras") },
                actions = {
                    TextButton(onClick = onScan) { Text("Suche") }
                    TextButton(onClick = onAdd) { Text("Neu") }
                    TextButton(onClick = { showView = true }) { Text("Ansicht") }
                },
            )
        },
        bottomBar = {
            if (pageCount > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = pagerState.currentPage > 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    ) { Text("< Zurück") }
                    Text("Seite ${pagerState.currentPage + 1} von $pageCount")
                    TextButton(
                        enabled = pagerState.currentPage < pageCount - 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    ) { Text("Weiter >") }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) { p ->
            PageGrid(
                cams = cameras.drop(p * perPage).take(perPage),
                perPage = perPage,
                onOpen = onOpen,
            )
        }
    }

    if (showView) {
        AlertDialog(
            onDismissRequest = { showView = false },
            title = { Text("Kameras pro Seite") },
            text = {
                Column {
                    PER_PAGE_OPTIONS.forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPerPage(n); showView = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = n == perPage, onClick = { onPerPage(n); showView = false })
                            Text(if (n == 1) "1 Kamera" else "$n Kameras")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showView = false }) { Text("Schließen") } },
        )
    }
}

@Composable
private fun PageGrid(cams: List<Camera>, perPage: Int, onOpen: (Camera) -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cols = if (landscape) {
        when {
            perPage <= 1 -> 1
            perPage <= 4 -> 2
            else -> 3
        }
    } else {
        when {
            perPage <= 2 -> 1
            perPage <= 6 -> 2
            else -> 3
        }
    }
    val rows = (perPage + cols - 1) / cols

    Column(Modifier.fillMaxSize().padding(4.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until cols) {
                    val cam = cams.getOrNull(r * cols + c)
                    Box(Modifier.weight(1f).fillMaxHeight().padding(4.dp)) {
                        if (cam != null) CameraTile(cam) { onOpen(cam) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraTile(cam: Camera, onClick: () -> Unit) {
    val uri = cam.rtspUri(main = false)
    Card(modifier = Modifier.fillMaxSize().clickable { onClick() }) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (uri != null) {
                CameraPlayer(uri = uri, modifier = Modifier.fillMaxSize(), muted = true)
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

// ---------------------------------------------------------------- Vollbild mit Ton und Schwenken

@Composable
private fun FullScreen(camera: Camera, onBack: () -> Unit, onEdit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var useMain by remember { mutableStateOf(!camera.preferSub) }
    var muted by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var ptzError by remember { mutableStateOf<String?>(null) }
    val ptz: PtzController? = remember(camera) {
        when {
            !camera.ptz -> null
            camera.ptzMode == Camera.PTZ_FOSCAM -> FoscamPtz(camera)
            else -> OnvifPtz(camera)
        }
    }

    DisposableEffect(ptz) {
        onDispose {
            if (ptz != null) CoroutineScope(Dispatchers.IO).launch { ptz.stop() }
        }
    }

    val uri = camera.rtspUri(useMain)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (uri != null) {
            CameraPlayer(
                uri = uri,
                modifier = Modifier.fillMaxSize(),
                muted = muted,
                reloadKey = reloadKey,
                onError = { msg ->
                    if (useMain) {
                        useMain = false
                        notice = "Hauptstream nicht abspielbar ($msg). Substream wird gezeigt."
                    }
                },
            )
        }

        Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
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
            notice?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.background(Color(0x99000000)).padding(6.dp),
                )
            }
            ptzError?.let {
                Text(
                    "Schwenken: $it",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.background(Color(0x99000000)).padding(6.dp),
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x66000000))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { muted = !muted }) { Text(if (muted) "Ton aus" else "Ton an") }
            TextButton(onClick = {
                useMain = !useMain
                notice = null
            }) { Text(if (useMain) "Stream: Haupt" else "Stream: Sub") }
            TextButton(onClick = {
                notice = null
                reloadKey += 1
            }) { Text("Neu laden") }
        }

        if (ptz != null) {
            PtzPad(
                ptz = ptz,
                onError = { ptzError = it },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}

@Composable
private fun PtzPad(ptz: PtzController, onError: (String?) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val press: (Float, Float) -> Unit = { x, y -> scope.launch { onError(ptz.move(x, y)) } }
    val release: () -> Unit = { scope.launch { ptz.stop() } }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HoldButton("▲", { press(0f, 0.5f) }, release)
        Row {
            HoldButton("◀", { press(-0.5f, 0f) }, release)
            Spacer(Modifier.size(56.dp))
            HoldButton("▶", { press(0.5f, 0f) }, release)
        }
        HoldButton("▼", { press(0f, -0.5f) }, release)
    }
}

@Composable
private fun HoldButton(label: String, onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(Color(0x99000000), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------- Kamera bearbeiten

@Composable
private fun EditScreen(
    camera: Camera,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Camera) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(camera.name) }
    var host by remember { mutableStateOf(camera.host) }
    var port by remember { mutableStateOf(camera.port.toString()) }
    var user by remember { mutableStateOf(camera.user) }
    var password by remember { mutableStateOf(camera.password) }
    var path by remember { mutableStateOf(camera.pathTemplate) }
    var preferSub by remember { mutableStateOf(camera.preferSub) }
    var ptz by remember { mutableStateOf(camera.ptz) }
    var onvifPort by remember { mutableStateOf(camera.onvifPort.toString()) }
    var ptzMode by remember { mutableStateOf(camera.ptzMode) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (isNew) "Kamera hinzufügen" else "Kamera einrichten",
            style = MaterialTheme.typography.titleLarge,
        )
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
            label = { Text("RTSP-Pfad (%d = 0 Haupt-, 1 Substream; oder Haupt|Sub)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { path = Camera.PATH_STANDARD }) { Text("Standard") }
            OutlinedButton(onClick = { path = Camera.PATH_ONVIF }) { Text("Mit ONVIF-Zusatz") }
        }
        OutlinedButton(onClick = {
            // Vorlage für Foscam FI9821W/P (bei Lupus als LE200 verkauft)
            port = "88"
            path = Camera.PATH_FOSCAM
            ptz = true
            ptzMode = Camera.PTZ_FOSCAM
            onvifPort = "80"
        }) { Text("Vorlage Foscam / LE200") }
        Text(
            "Standard passt für Lupus LE202/LE204/LE232 und Imou. Falls das Bild nicht kommt, " +
                "\"Mit ONVIF-Zusatz\" probieren (z. B. LE221). Die LE200 ist eine Foscam FI9821: dafür " +
                "\"Vorlage Foscam / LE200\" antippen (RTSP-Port 88, Steuer-Port 80; die Ports kannst du " +
                "anpassen). Imou: Benutzer admin, als Passwort das in der Imou-Life-App vergebene " +
                "Gerätepasswort bzw. der Sicherheitscode; RTSP/ONVIF muss dort ggf. aktiviert sein.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = preferSub, onCheckedChange = { preferSub = it })
            Text("  Im Vollbild gleich den Substream zeigen")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ptz, onCheckedChange = { ptz = it })
            Text("  Schwenken/Neigen")
        }
        if (ptz) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = ptzMode == Camera.PTZ_ONVIF, onClick = { ptzMode = Camera.PTZ_ONVIF })
                Text("ONVIF")
                RadioButton(selected = ptzMode == Camera.PTZ_FOSCAM, onClick = { ptzMode = Camera.PTZ_FOSCAM })
                Text("Foscam")
            }
            OutlinedTextField(
                onvifPort, { onvifPort = it.filter(Char::isDigit) },
                label = { Text("Steuer-Port (Weboberfläche, meist 80)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                onSave(
                    camera.copy(
                        name = name.trim().ifBlank { camera.name },
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 554,
                        user = user.trim(),
                        password = password,
                        pathTemplate = path.trim().ifBlank { Camera.DEFAULT_PATH },
                        preferSub = preferSub,
                        ptz = ptz,
                        onvifPort = onvifPort.toIntOrNull() ?: 80,
                        ptzMode = ptzMode,
                    )
                )
            }) { Text("Speichern") }
            TextButton(onClick = onCancel) { Text("Abbrechen") }
            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kamera löschen?") },
            text = { Text("\"${camera.name}\" wird aus der App entfernt.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

// ---------------------------------------------------------------- Netzwerksuche

@Composable
private fun ScanScreen(
    existingHosts: List<String>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prefix by remember { mutableStateOf(NetworkScanner.localPrefix(context) ?: "192.168.178") }
    var scanning by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf(listOf<FoundDevice>()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kameras im Netzwerk suchen", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Gesucht werden Geräte mit offenem Kamera-Port (RTSP, 554) in einem Adressbereich. " +
                        "Zuhause im WLAN wird der Bereich automatisch erkannt. Über das VPN trägst du den " +
                        "Bereich deines Heimnetzes ein (die ersten drei Blöcke, z. B. 192.168.178).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    prefix, { prefix = it },
                    label = { Text("Adressbereich (z. B. 192.168.178)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !scanning,
                        onClick = {
                            scanning = true
                            finished = false
                            progress = 0
                            found = emptyList()
                            scope.launch {
                                NetworkScanner.scan(
                                    prefix = prefix.trim().trimEnd('.'),
                                    onProgress = { p -> scope.launch { progress = p } },
                                    onFound = { d ->
                                        scope.launch {
                                            found = (found + d).sortedBy {
                                                it.ip.substringAfterLast('.').toIntOrNull() ?: 0
                                            }
                                        }
                                    },
                                )
                                scanning = false
                                finished = true
                            }
                        },
                    ) { Text(if (scanning) "Suche läuft..." else "Suchen") }
                    TextButton(onClick = onBack) { Text("Zurück") }
                }
                if (scanning) {
                    LinearProgressIndicator(
                        progress = { progress / 254f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$progress von 254 Adressen geprüft", style = MaterialTheme.typography.bodySmall)
                }
                if (finished && found.isEmpty()) {
                    Text("Keine Kamera gefunden. Bereich prüfen oder das Handy mit dem Heimnetz verbinden.")
                }
            }
        }
        items(found) { d ->
            val exists = existingHosts.contains(d.ip)
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(d.ip, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (d.http) "Kamera-Port offen, Weboberfläche vorhanden" else "Kamera-Port offen",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(enabled = !exists, onClick = { onAdd(d.ip) }) {
                        Text(if (exists) "Vorhanden" else "Hinzufügen")
                    }
                }
            }
        }
    }
}
