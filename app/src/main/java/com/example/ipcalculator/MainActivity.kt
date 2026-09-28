package com.example.ipcalculator

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ipcalculator.ui.theme.IpCalculatorTheme
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.YandexAds
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IpCalculatorTheme {
                IpCalculatorScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpCalculatorScreen() {
    var ip by remember { mutableStateOf("192.168.1.10") }
    var mask by remember { mutableStateOf("24") }
    var result by remember { mutableStateOf<IpCalculator.SubnetResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    var newPrefix by remember { mutableStateOf("25") }
    var split by remember { mutableStateOf<IpCalculator.SplitResult?>(null) }
    var appliedPrefix by remember { mutableStateOf<Int?>(null) }
    var splitError by remember { mutableStateOf<String?>(null) }
    var splitCopied by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current   // ОДИН раз

    // --- Consent (ФЗ-152) ---
    var consent by remember { mutableStateOf<Boolean?>(null) }

    fun saveConsent(value: Boolean) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("consent_given", value)
        }
        YandexAds.setUserConsent(value)
        consent = value
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("consent_given")) {
            val saved = prefs.getBoolean("consent_given", false)
            YandexAds.setUserConsent(saved)
            consent = saved
        }
    }

    if (consent == null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Согласие на обработку данных") },
            text = { Text("Приложение показывает рекламу. Для подбора релевантных объявлений обрабатываются обезличенные данные устройства. Разрешить персонализацию рекламы?") },
            confirmButton = {
                TextButton(onClick = { saveConsent(true) }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = { saveConsent(false) }) { Text("Только общая реклама") }
            }
        )
    }

    // --- Экран с баннером ---
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val currentResult = result ?: return@rememberLauncherForActivityResult
        val np = appliedPrefix ?: return@rememberLauncherForActivityResult

        scope.launch {
            val saveResult = saveSubnetsToFile(context, uri, currentResult, np)
            if (saveResult.isSuccess) {
                snackbarHostState.showSnackbar("Сохранено ✓")
            } else {
                val errorMessage = saveResult.exceptionOrNull()?.message ?: "Ошибка"
                snackbarHostState.showSnackbar("Ошибка: $errorMessage")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            // Баннер появляется только после явного ответа пользователя
            if (consent != null) {
                AndroidView(
                    factory = { ctx ->
                        val density = ctx.resources.displayMetrics.density
                        val adWidthDp = (ctx.resources.displayMetrics.widthPixels / density).roundToInt()

                        BannerAdView(ctx).apply {
                            setAdSize(BannerAdSize.sticky(ctx, adWidthDp))
                            loadAd(AdRequest.Builder("demo-banner-yandex").build())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; error = null; copied = false },
                label = { Text("IP-адрес") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = mask,
                onValueChange = { mask = it; error = null; copied = false },
                label = { Text("Маска или префикс") },
                placeholder = { Text("255.255.255.0 или 24") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    copied = false
                    split = null
                    splitError = null
                    splitCopied = false
                    appliedPrefix = null
                    try {
                        val calc = IpCalculator.calculate(ip, mask)
                        result = calc
                        error = null
                        newPrefix = if (calc.cidr < 32) (calc.cidr + 1).toString() else "32"
                    } catch (e: IllegalArgumentException) {
                        result = null
                        error = e.message
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Рассчитать") }

            error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            result?.let { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResultRow("Сеть", "${r.networkAddress}/${r.cidr}")
                        ResultRow("Маска", r.subnetMask)
                        ResultRow("Wildcard", r.wildcardMask)
                        ResultRow("Broadcast", r.broadcastAddress)
                        ResultRow("Первый хост", r.firstUsableIp)
                        ResultRow("Последний хост", r.lastUsableIp)
                        ResultRow("Всего адресов", r.totalAddresses.toString())
                        ResultRow("Доступно хостов", r.usableHosts.toString())

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("IP Calculator", r.toText()))
                                    )
                                }
                                copied = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (copied) "Скопировано ✓" else "Скопировать") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Разбиение на подсети",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = newPrefix,
                            onValueChange = { newPrefix = it; splitError = null; splitCopied = false },
                            label = { Text("Новый префикс (${r.cidr + 1}..32)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                splitCopied = false
                                try {
                                    val np = IpCalculator.parsePrefix(newPrefix)
                                    split = IpCalculator.splitNetwork(r.networkAddress, r.cidr, np, limit = 50)
                                    splitError = null
                                    appliedPrefix = np
                                } catch (e: IllegalArgumentException) {
                                    split = null
                                    splitError = e.message
                                    appliedPrefix = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Разбить") }

                        splitError?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        split?.let { s ->
                            Text(
                                text = if (s.totalCount > s.subnets.size) {
                                    "Всего подсетей: ${s.totalCount}, показаны первые ${s.subnets.size}"
                                } else {
                                    "Всего подсетей: ${s.totalCount}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            s.subnets.forEachIndexed { index, subnet ->
                                SubnetCard(index + 1, subnet)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText(
                                                    "Subnets",
                                                    s.subnets.joinToString("\n\n") { it.toText() }
                                                )
                                            )
                                        )
                                    }
                                    splitCopied = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (splitCopied) "Скопировано ✓" else "Скопировать список") }
                            Button(
                                onClick = {
                                    val fileName = "subnets_${r.networkAddress.replace(".", "_")}_slash${r.cidr}.txt"
                                    saveLauncher.launch(fileName)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Сохранить в txt") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetCard(number: Int, subnet: IpCalculator.SubnetResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "№$number  ${subnet.networkAddress}/${subnet.cidr}",
                style = MaterialTheme.typography.titleSmall
            )
            ResultRow("Маска", subnet.subnetMask)
            ResultRow("Broadcast", subnet.broadcastAddress)
            ResultRow("Первый хост", subnet.firstUsableIp)
            ResultRow("Последний хост", subnet.lastUsableIp)
            ResultRow("Хостов", subnet.usableHosts.toString())
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private suspend fun saveSubnetsToFile(
    context: Context,
    uri: Uri,
    original: IpCalculator.SubnetResult,
    newPrefix: Int
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            IpCalculator.writeSplitToStream(outputStream, original, newPrefix)
        } ?: return@withContext Result.failure(Exception("Не удалось открыть поток для записи"))

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}