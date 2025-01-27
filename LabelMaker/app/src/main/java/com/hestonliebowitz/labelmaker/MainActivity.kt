package com.hestonliebowitz.labelmaker

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hestonliebowitz.labelmaker.ui.theme.LabelMakerTheme
import kotlinx.coroutines.launch

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

import org.json.JSONArray
import org.json.JSONObject


data class Settings(var endpoint: String, var authToken: String)

const val DEFAULT = "default"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun getSettings(): Settings {
            val prefs = getSharedPreferences(DEFAULT, Context.MODE_PRIVATE)
            return Settings(
                endpoint = prefs.getString(
                    getString(R.string.pref_endpoint),
                    ""
                ) as String,
                authToken = prefs.getString(
                    getString(R.string.pref_auth_token),
                    ""
                ) as String
            )
        }

        val initialSettings = getSettings()
        val initialShowSettings = initialSettings.endpoint.isEmpty() || initialSettings.authToken.isEmpty()

        val history = HistoryService(applicationContext)
        val historyItems = mutableStateListOf(*history.getAll().toTypedArray())

        fun saveSettings(settings: Settings) {
            val prefs = getSharedPreferences(DEFAULT, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString(
                getString(R.string.pref_endpoint),
                settings.endpoint
            )
            editor.putString(
                getString(R.string.pref_auth_token),
                settings.authToken
            )
            editor.apply()
        }

        fun resetHistoryView() {
            historyItems.clear()
            historyItems.addAll(history.getAll())
        }

        fun fullSend(value: String?, qty: Int = 1) {
            if (value.isNullOrEmpty()) {
                Toast
                    .makeText(
                        applicationContext,
                        getString(R.string.empty_error),
                        Toast.LENGTH_LONG
                    )
                    .show()
                return
            }

            lifecycleScope.launch {
                val settings = getSettings()
                val client = HttpClient(CIO)
                val endpoint = settings.endpoint
                var message: String

                val labelObj = JSONObject()
                labelObj.put("body", value)
                labelObj.put("qty", qty)
                val requestBody = JSONObject()
                requestBody.put("items", JSONArray(listOf(labelObj)))

                try {
                    val response: HttpResponse = client.post(endpoint) {
                        headers {
                            append(HttpHeaders.Authorization, settings.authToken)
                        }
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }
                    if (response.status == HttpStatusCode.OK) {
                        history.save(value)
                        message = getString(R.string.print_success)
                    } else {
                        message = "${getString(R.string.print_error)}: ${response.status.description}"
                    }
                } catch (e: Throwable) {
                    message = "${getString(R.string.print_error)}: $e"
                }

                client.close()
                Toast
                    .makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_LONG
                    )
                    .show()
            }
        }

        fun hideKeyboard() {
            this.currentFocus?.let { view ->
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }

        setContent {
            LabelMakerTheme {
                var showSettings by remember { mutableStateOf(initialShowSettings) }
                MainApp(
                    onPrint = { value, qty ->
                        fullSend(value, qty)
                    },
                    onChangeSettings = {
                        hideKeyboard()
                        showSettings = true
                    },
                    defaultQty = 1,
                    historyItems = historyItems
                )

                var viewSettings by remember { mutableStateOf(initialSettings) }
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Settings(
                        settings = viewSettings,
                        onSettingsChanged = {settings: Settings ->
                            viewSettings = settings
                            saveSettings(settings)
                            showSettings = false
                        },
                        onCancel = {
                            showSettings = false
                        },
                        resetHistory = {
                            history.deleteAll()
                            resetHistoryView()
                            Toast
                                .makeText(
                                    applicationContext,
                                    getString(R.string.history_cleared),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        },
                        historyItems = historyItems
                    )
                }
            }
        }
    }
}

@Composable
fun Settings(
    settings: Settings,
    onSettingsChanged: (settings: Settings) -> Unit,
    onCancel: () -> Unit,
    resetHistory: () -> Unit,
    historyItems: SnapshotStateList<String>
) {
    var endpointValue by remember { mutableStateOf(settings.endpoint) }
    var authTokenValue by remember { mutableStateOf(settings.authToken) }
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = onCancel
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
                TextButton(
                    onClick = {
                        onSettingsChanged(settings) }
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            OutlinedTextField(
                value = endpointValue,
                onValueChange = {
                    endpointValue = it
                    settings.endpoint = it
                },
                label = {
                    Text(stringResource(R.string.label_printer_endpoint))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            OutlinedTextField(
                value = authTokenValue,
                onValueChange = {
                    authTokenValue = it
                    settings.authToken = it
                },
                label = {
                    Text(stringResource(R.string.label_auth_token))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row (
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    enabled = historyItems.isNotEmpty(),
                    onClick = { showDialog = true }) {
                    Text(text = stringResource(R.string.reset_history))
                }
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        icon = { Icon(Icons.Filled.Warning, contentDescription = "Danger", tint = MaterialTheme.colorScheme.primary) },
                        title = { Text(stringResource(id = R.string.confirm_deletion)) },
                        text = { Text(stringResource(id = R.string.confirm_deletion_explanation)) },
                        confirmButton = {
                            Button(onClick = {
                                // Perform the action here
                                resetHistory()
                                showDialog = false
                            }) {
                                Text(stringResource(id = R.string.reset_history))
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showDialog = false }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SettingsPreview() {
    val items = remember { mutableStateListOf<String>() }
    LabelMakerTheme {
        Settings(
            Settings(
                endpoint = "https://foo.bar/baz",
                authToken = "<auth_token>"
            ),
            onSettingsChanged = {},
            onCancel = {},
            resetHistory = {},
            historyItems = items
        )
    }
}

@Preview(
    showBackground = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Dark"
)
@Composable
fun DarkSettingsPreview() {
    val items = remember { mutableStateListOf("Item") }
    LabelMakerTheme {
        Settings(
            Settings(
                endpoint = "https://foo.bar/baz",
                authToken = "<auth_token>"
            ),
            onSettingsChanged = {},
            onCancel = {},
            resetHistory = {},
            historyItems = items
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    onPrint: (value: String?, qty: Int) -> Unit,
    onChangeSettings: () -> Unit,
    defaultQty: Int = 1,
    historyItems: SnapshotStateList<String> = mutableStateListOf()
) {
    var lastTextValue by remember { mutableStateOf("") }
    var defaultTextChanged by remember { mutableStateOf(false) }
    var printQty by remember { mutableIntStateOf(defaultQty) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun submit() {
        focusManager.clearFocus()
        onPrint(if (defaultTextChanged) lastTextValue else null, printQty)
    }

    fun reset() {
        lastTextValue = ""
        printQty = 1
        defaultTextChanged = false
    }

    fun Modifier.horizontalFade(
        fadeWidth: Dp = 8.dp,
        fadeOffset: Dp = 8.dp
    ): Modifier {
        return graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithContent {
            drawContent()
            // Left fade
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black
                    ),
                    startX = fadeOffset.toPx(),
                    endX = fadeOffset.toPx() + fadeWidth.toPx()
                ),
                blendMode = BlendMode.DstIn
            )
            // Right fade
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Transparent
                    ),
                    startX = size.width - fadeOffset.toPx() - fadeWidth.toPx(),
                    endX = size.width - fadeOffset.toPx()
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Row {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_label_24),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .align(Alignment.CenterVertically),
                        )
                        Text(text = stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onChangeSettings()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(
                        onClick = { reset() },
                        modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.reset),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = printQty == 1,
                            onClick = { printQty = 1 },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 0,
                                count = 3
                            )
                        ) {
                            Text(stringResource(R.string.digit_1))
                        }
                        SegmentedButton(
                            selected = printQty == 2,
                            onClick = { printQty = 2 },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 1,
                                count = 3
                            )
                        ) {
                            Text(stringResource(R.string.digit_2))
                        }
                        SegmentedButton(
                            selected = printQty == 3,
                            onClick = { printQty = 3 },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 2,
                                count = 3
                            )
                        ) {
                            Text(stringResource(R.string.digit_3))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    FloatingActionButton(
                        onClick = { submit() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = stringResource(R.string.print),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
    ) {contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFade()
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 0.dp, top = 8.dp, end = 0.dp)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    for (item in historyItems) {
                        ElevatedSuggestionChip(
                            modifier = Modifier.padding(
                                start = if (item == historyItems.first()) 16.dp else 0.dp,
                                end = if (item == historyItems.last()) 16.dp else 0.dp
                            ),
                            onClick = {
                                lastTextValue = item
                                defaultTextChanged = true
                            },
                            label = { Text(item) }
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .padding(0.dp)
                    .fillMaxHeight()
                    ) {
                BasicTextField(
                    value = lastTextValue,
                    onValueChange = {
                        lastTextValue = it
                        defaultTextChanged = true
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxHeight(),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (lastTextValue.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.placeholder),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val history = remember {
        mutableStateListOf("A recent label", "Another label", "Third", "Fourth")
    }
    LabelMakerTheme {
        MainApp(
            onPrint = { _, _ ->},
            onChangeSettings = {},
            defaultQty = 3,
            historyItems = history
        )
    }
}

@Preview(
    showBackground = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Dark"
)
@Composable
fun DarkPreview() {
    val history = remember {
        mutableStateListOf<String>()
    }
    LabelMakerTheme {
        MainApp(
            onPrint = { _, _ ->},
            onChangeSettings = {},
            defaultQty = 2,
            historyItems = history
        )
    }
}
