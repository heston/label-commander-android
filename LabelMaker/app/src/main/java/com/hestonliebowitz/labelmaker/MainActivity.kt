package com.hestonliebowitz.labelmaker

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hestonliebowitz.labelmaker.ui.theme.LabelMakerTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.json.JSONArray
import org.json.JSONObject

data class Settings(var endpoint: String, var authToken: String)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun getSettings(): Settings {
            val prefs = getPreferences(Context.MODE_PRIVATE)
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

        fun saveSettings(settings: Settings) {
            val prefs = getPreferences(Context.MODE_PRIVATE)
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

        fun fullSend(label: String?) {
            if (label == null || label.isEmpty()) {
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
                labelObj.put("body", label)
                labelObj.put("qty", 1)
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

                    message = if (response.status == HttpStatusCode.OK) {
                        getString(R.string.print_success)
                    } else {
                        "${getString(R.string.print_error)}: ${response.status.description}"
                    }
                } catch (e: Throwable) {
                    message = "${getString(R.string.print_error)}: $e"
                }

                Toast
                    .makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_LONG
                    )
                    .show()
                client.close()
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
                    onPrint = {
                        fullSend(it)
                    },
                    onChangeSettings = {
                        hideKeyboard()
                        showSettings = true
                    }
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
                        }
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
    onCancel: () -> Unit
) {
    var endpointValue by remember { mutableStateOf(settings.endpoint) }
    var authTokenValue by remember { mutableStateOf(settings.authToken) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                        Icons.Filled.Close,
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
        }
    }
}

@Preview
@Composable
fun SettingsPreview() {
    LabelMakerTheme {
        Settings(
            Settings(
                endpoint = "https://foo.bar/baz",
                authToken = "asdf"
            ),
            onSettingsChanged = {},
            onCancel = {}
        )
    }
}

@Composable
fun MainApp(onPrint: (value: String?) -> Unit, onChangeSettings: () -> Unit) {
    var lastTextValue by remember { mutableStateOf("") }
    var defaultTextChanged by remember { mutableStateOf(false) }
    val scaffoldState = rememberScaffoldState()
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus()
        onPrint(if (defaultTextChanged) lastTextValue else null)
    }
    
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Icon(
                            painter=painterResource(id = R.drawable.baseline_label_24),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onChangeSettings()
                    }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(
            onClick = { submit() },
            backgroundColor = MaterialTheme.colors.primary
        ) {
            Icon(
                Icons.Filled.Print,
                contentDescription = stringResource(R.string.print),
                tint = MaterialTheme.colors.onPrimary
            )
        }}
    ) {contentPadding ->
        Surface(modifier = Modifier.padding(contentPadding)) {
            BasicTextField(
                value = lastTextValue,
                onValueChange = {
                    lastTextValue = it
                    defaultTextChanged = true
                },
                modifier = Modifier
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.h1,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (lastTextValue.isEmpty()) {
                            Text(
                                text = stringResource(R.string.placeholder),
                                style = MaterialTheme.typography.h1,
                                color = Color.LightGray
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LabelMakerTheme {
        MainApp(
            onPrint = {},
            onChangeSettings = {}
        )
    }
}