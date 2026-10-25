package ru.homegrid.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF101B2D)
private val Panel = Color(0xFF19263A)
private val Accent = Color(0xFF62D6A5)
private val Muted = Color(0xFF9AAAC0)

data class Telemetry(
    val batteryV: Double = 12.8, val batteryA: Double = 4.2,
    val soc: Int = 78, val pvW: Int = 640, val loadW: Int = 385,
    val gridV: Double = 230.4, val outputV: Double = 230.0,
    val frequency: Double = 50.0, val temperature: Double = 34.0,
    val status: String = "Демонстрационный режим", val connected: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeGridApp() }
    }
}

@Composable
fun HomeGridApp() {
    var tab by remember { mutableStateOf(0) }
    var address by remember { mutableStateOf("http://192.168.0.113:18899") }
    var endpoint by remember { mutableStateOf("/api/status") }
    var telemetry by remember { mutableStateOf(Telemetry()) }
    var message by remember { mutableStateOf("Показаны демонстрационные данные") }
    var refresh by remember { mutableStateOf(true) }

    LaunchedEffect(address, endpoint, refresh) {
        if (!refresh) return@LaunchedEffect
        while (true) {
            try {
                val result = withContext(Dispatchers.IO) { fetchStatus(address.trimEnd('/') + endpoint) }
                telemetry = result
                message = "Данные получены: ${address.trimEnd('/') + endpoint}"
            } catch (e: Exception) {
                telemetry = telemetry.copy(connected = false, status = "Нет соединения")
                message = "Нет соединения: ${e.message ?: "проверьте адрес и API"}"
            }
            delay(2000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Navy, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = Navy, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Обзор") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.BatteryChargingFull, null) }, label = { Text("АКБ / BMS") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Графики") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("HOMEGRID", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Энергосистема", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = if (telemetry.connected) Color(0xFF164C3B) else Color(0xFF3D3440),
                        shape = RoundedCornerShape(20.dp)) {
                        Text(if (telemetry.connected) "● ONLINE" else "● DEMO / OFFLINE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(message, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    0 -> Overview(telemetry)
                    1 -> Battery(telemetry)
                    2 -> Charts(telemetry)
                    else -> Settings(address, { address = it }, endpoint, { endpoint = it },
                        refresh, { refresh = it }, { message = "Проверка подключения будет выполнена автоматически" })
                }
                Spacer(Modifier.height(18.dp))
                Text("HomeGrid RU • бесплатный локальный монитор", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun Overview(t: Telemetry) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Поток энергии", color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                FlowNode("☀", "Солнце", "${t.pvW} Вт")
                Icon(Icons.Default.ArrowForward, null, tint = Accent)
                FlowNode("▰", "АКБ", "${t.batteryV} В")
                Icon(Icons.Default.ArrowForward, null, tint = Accent)
                FlowNode("⌂", "Дом", "${t.loadW} Вт")
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Заряд АКБ", "${t.soc}%", "SOC", Modifier.weight(1f), Accent)
                Metric("Солнечная мощность", "${t.pvW} Вт", "PV", Modifier.weight(1f), Color(0xFFFFC66D))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Нагрузка", "${t.loadW} Вт", "AC OUT", Modifier.weight(1f), Color(0xFF82B7FF))
                Metric("Сеть", "${t.gridV} В", "AC IN", Modifier.weight(1f), Color(0xFFD5A4FF))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle("Инвертор")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("Выход", "${t.outputV} В", "230 В ном.", Modifier.weight(1f))
        Metric("Частота", "${t.frequency} Гц", "AC", Modifier.weight(1f))
        Metric("Температура", "${t.temperature} °C", "Инвертор", Modifier.weight(1f))
    }
}
@Composable
fun FlowNode(symbol: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Text(symbol, color = Accent, fontSize = 25.sp)
        Text(label, fontSize = 12.sp)
        Text(value, color = Muted, fontSize = 11.sp)
    }
}
@Composable
fun Metric(title: String, value: String, foot: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(7.dp))
            Text(value, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(foot, color = Muted, fontSize = 10.sp)
        }
    }
}
@Composable
fun SectionTitle(s: String) {
    Text(s, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
}
@Composable
fun Battery(t: Telemetry) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Аккумулятор", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("${t.soc}%", fontSize = 42.sp, color = Accent, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { t.soc / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Accent, trackColor = Navy)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Напряжение", "${t.batteryV} В", "АКБ", Modifier.weight(1f))
                Metric("Ток", "${t.batteryA} А", "заряд / разряд", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Мощность", "${(t.batteryV * t.batteryA).toInt()} Вт", "расчёт", Modifier.weight(1f))
                Metric("Температура", "${t.temperature} °C", "датчик инвертора", Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle("Данные BMS")
    InfoRow("Напряжения ячеек", "Требуется адаптер BMS")
    InfoRow("Балансировка", "Нет данных")
    InfoRow("SOH / ошибки", "Нет данных")
    Text("Данные ячеек появятся после реализации протокола конкретной BMS.", color = Muted, fontSize = 12.sp)
}
@Composable
fun InfoRow(a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(a, color = Muted)
        Text(b)
    }
    HorizontalDivider(color = Color(0xFF2B3A50))
}
@Composable
fun Charts(t: Telemetry) {
    SectionTitle("История показателей")
    Text("Демонстрационный график • для реальной истории нужен источник телеметрии", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Мощность, Вт", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleChart(listOf(310f, 370f, 350f, 520f, 470f, 610f, t.pvW.toFloat(), 560f))
            Spacer(Modifier.height(18.dp))
            Text("Заряд АКБ, %", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SimpleChart(listOf(65f, 66f, 68f, 67f, 71f, 73f, 76f, t.soc.toFloat()))
        }
    }
}
@Composable
fun SimpleChart(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val max = (values.maxOrNull() ?: 100f).coerceAtLeast(1f) * 1.15f
        for (i in 0..3) {
            val y = size.height * i / 3
            drawLine(Color(0xFF314057), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).coerceAtLeast(1)
            val y = size.height - (v / max) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}
@Composable
fun Settings(address: String, onAddress: (String) -> Unit, endpoint: String, onEndpoint: (String) -> Unit,
             refresh: Boolean, onRefresh: (Boolean) -> Unit, onTest: () -> Unit) {
    SectionTitle("Подключение по Wi‑Fi")
    Text("Укажите адрес локального API вашего инвертора или совместимого Wi‑Fi шлюза. У многих инверторов нет HTTP API — им нужен отдельный протокольный адаптер.", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(address, onAddress, label = { Text("Адрес устройства / шлюза") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(endpoint, onEndpoint, label = { Text("Путь API статуса") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Автообновление каждые 2 секунды", modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = refresh, onCheckedChange = onRefresh)
    }
    Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text("Проверить подключение") }
    Spacer(Modifier.height(18.dp))
    SectionTitle("Параметры протокола")
    InfoRow("HTTP JSON API", "Базовый адаптер")
    InfoRow("Modbus TCP / UDP", "Нужно добавить профиль модели")
    InfoRow("Solarman V5", "Нужно добавить профиль регистратора")
    InfoRow("BMS", "Нужен отдельный адаптер")
    Text("Перед записью настроек инвертора необходимо проверить модель и карту регистров. Сейчас запись параметров не выполняется.", color = Color(0xFFFFC66D), fontSize = 12.sp)
}

private fun fetchStatus(url: String): Telemetry {
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.connectTimeout = 3500
    conn.readTimeout = 3500
    conn.requestMethod = "GET"
    conn.setRequestProperty("Accept", "application/json")
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val live = root.optJSONObject("live") ?: root
        val connected = root.optBoolean("connected", true) && root.optBoolean("available", true)
        fun num(obj: JSONObject, vararg keys: String, default: Double): Double {
            for (k in keys) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val value = obj.optDouble(k, Double.NaN)
                    if (!value.isNaN() && !value.isInfinite()) return value
                    val nested = obj.optJSONObject(k)
                    if (nested != null && nested.has("value_pct")) {
                        val nestedValue = nested.optDouble("value_pct", Double.NaN)
                        if (!nestedValue.isNaN() && !nestedValue.isInfinite()) return nestedValue
                    }
                }
            }
            return default
        }
        val batterySoc = live.optJSONObject("battery_soc")
        val socValue = if (batterySoc != null) {
            batterySoc.optDouble("value_pct", num(live, "soc", "battery_percent", default = 0.0))
        } else {
            num(live, "soc", "battery_percent", default = 0.0)
        }
        val statusText = when {
            !connected -> "Нет соединения"
            root.optString("status_text").isNotBlank() -> root.optString("status_text")
            live.optString("modeText").isNotBlank() -> live.optString("modeText")
            else -> "Подключено"
        }
        return Telemetry(
            batteryV = num(live, "batV", "battery_voltage", "batteryV", "bat_voltage", default = 0.0),
            batteryA = num(live, "batA", "battery_current", "batteryA", "bat_current", default = 0.0),
            soc = socValue.toInt().coerceIn(0, 100),
            pvW = num(live, "pvTotalW", "pvW", "pv_power", "solar_power", default = 0.0).toInt(),
            loadW = num(live, "outW", "load_power", "loadW", "output_power", default = 0.0).toInt(),
            gridV = num(live, "mainsV", "grid_voltage", "gridV", "ac_input_voltage", default = 0.0),
            outputV = num(live, "outV", "output_voltage", "outputV", "ac_output_voltage", default = 0.0),
            frequency = num(live, "outHz", "frequency", "output_frequency", default = 0.0),
            temperature = num(live, "invC", "temperature", "inverter_temperature", default = 0.0),
            status = statusText,
            connected = connected
        )
    } finally {
        conn.disconnect()
    }
}
