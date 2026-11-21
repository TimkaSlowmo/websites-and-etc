package kz.okno.proyom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun ScreenTitle(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("История")
        if (vm.history.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.History, null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                Text("Пока пусто", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Сохраняйте расчёты кнопкой «Сохранить в историю».", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.history, key = { it.id }) { item ->
                    Row(
                        Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                            .clickable { vm.open(item) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val thumb = remember(item.id) { vm.thumbnail(item) }
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFF2F5))) {
                            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("${item.spec.type.title} ${item.spec.widthMm}×${item.spec.heightMm}")
                            Text(fmt.format(Date(item.date)), fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(Money.format(item.total, item.currency), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { vm.delete(item) }) { Icon(Icons.Filled.Delete, "Удалить", tint = Color.Gray) }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val c = vm.catalog
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Каталог")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(c.name) {
                Line("Валюта", c.currency)
                Line("Наценка", "${c.markupPercent.toInt()}%")
                Line("НДС", "${c.vatPercent.toInt()}%")
            }
            SectionCard("Профили, за м²") { c.profiles.forEach { Line(it.name, Money.format(it.price, c.currency)) } }
            SectionCard("Стеклопакеты, за м²") { c.glass.forEach { Line(it.name, Money.format(it.price, c.currency)) } }
            if (c.hardware.isNotEmpty()) SectionCard("Фурнитура, за створку") { c.hardware.forEach { Line(it.name, Money.format(it.price, c.currency)) } }
            if (c.colors.isNotEmpty()) SectionCard("Цвет, наценка к профилю") { c.colors.forEach { Line(it.name, "+${it.surchargePercent.toInt()}%") } }
            if (c.extras.isNotEmpty()) SectionCard("Дополнительно") { c.extras.forEach { Line(it.name, "${Money.format(it.price, c.currency)} ${it.unit.title}") } }
            SectionCard("Монтаж") {
                Line("За м²", Money.format(c.installPerM2, c.currency))
                Line("Минимум", Money.format(c.installMin, c.currency))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Line(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Text(value, color = Color.Gray, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        message = if (text == null) "Не удалось открыть файл" else vm.importCatalog(text)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Настройки")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Коммерческое предложение") {
                OutlinedTextField(vm.company, vm::changeCompany, label = { Text("Название компании") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            SectionCard("Каталог и цены") {
                Line("Текущий", vm.catalog.name)
                Line("Профилей / стеклопакетов", "${vm.catalog.profiles.size} / ${vm.catalog.glass.size}")
                FilledTonalButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.size(8.dp)); Text("Загрузить каталог (JSON)")
                }
                TextButton(onClick = { vm.resetCatalog(); message = "Возвращён пример каталога" }) {
                    Text("Вернуть пример каталога", color = Color(0xFFDC2626))
                }
                message?.let { Text(it, fontSize = 13.sp, color = Accent) }
            }
            SectionCard("ИИ-сервер") {
                OutlinedTextField(vm.workerUrl, vm::changeWorkerUrl, label = { Text("Адрес") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Text("Уже настроено, менять не нужно.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
            SectionCard {
                Line("Версия", BuildConfig.VERSION_NAME)
                Text("Проём — ИИ-расчёт окон и дверей по чертежу.", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
