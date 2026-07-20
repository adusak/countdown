package com.example.countdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Properties
import kotlin.math.abs

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val STORE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val CONFIG_FILE = File(System.getProperty("user.home"), ".countdown.properties")

private val INPUT_FORMATS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
)

/** Parses a user/CLI supplied target, accepting several common date-time formats. */
private fun parseTarget(raw: String): LocalDateTime? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    for (fmt in INPUT_FORMATS) {
        try {
            return LocalDateTime.parse(text, fmt)
        } catch (_: DateTimeParseException) {
        }
    }
    return try {
        LocalDate.parse(text).atStartOfDay()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Fallback target: midnight on the next New Year's Day. */
private fun defaultTarget(): LocalDateTime {
    val now = LocalDateTime.now()
    return LocalDateTime.of(now.year + 1, 1, 1, 0, 0, 0)
}

private fun loadSavedTarget(): LocalDateTime? {
    return try {
        if (!CONFIG_FILE.exists()) return null
        val props = Properties()
        CONFIG_FILE.inputStream().use { props.load(it) }
        val rawTarget = props.getProperty("target") ?: return null
        parseTarget(rawTarget).also { parsed ->
            if (parsed == null) {
                System.err.println("Ignoring invalid target in ${CONFIG_FILE.absolutePath}: $rawTarget")
            }
        }
    } catch (error: IOException) {
        reportConfigError("read", error)
        null
    } catch (error: IllegalArgumentException) {
        reportConfigError("parse", error)
        null
    } catch (error: SecurityException) {
        reportConfigError("access", error)
        null
    }
}

private fun saveTarget(target: LocalDateTime): String? {
    return try {
        val props = Properties()
        props.setProperty("target", target.format(STORE_FORMAT))
        CONFIG_FILE.outputStream().use { props.store(it, "Countdown target") }
        null
    } catch (error: IOException) {
        reportConfigError("write", error)
        "Target updated, but it could not be saved."
    } catch (error: SecurityException) {
        reportConfigError("access", error)
        "Target updated, but it could not be saved."
    }
}

private fun reportConfigError(action: String, error: Exception) {
    val details = error.message ?: error.javaClass.simpleName
    System.err.println("Could not $action ${CONFIG_FILE.absolutePath}: $details")
}

/** Resolution order: CLI arg -> saved file -> COUNTDOWN_TARGET env -> default. */
private fun resolveInitialTarget(args: Array<String>): LocalDateTime {
    args.firstOrNull()?.let { arg -> parseTarget(arg)?.let { return it } }
    loadSavedTarget()?.let { return it }
    System.getenv("COUNTDOWN_TARGET")?.let { env -> parseTarget(env)?.let { return it } }
    return defaultTarget()
}

private data class Remaining(
    val past: Boolean,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

private fun remainingTo(target: LocalDateTime, now: LocalDateTime): Remaining {
    val duration = Duration.between(now, target)
    val total = abs(duration.seconds)
    return Remaining(
        past = duration.isNegative,
        days = total / 86_400,
        hours = (total % 86_400) / 3_600,
        minutes = (total % 3_600) / 60,
        seconds = total % 60,
    )
}

fun main(args: Array<String>) {
    val initialTarget = resolveInitialTarget(args)
    application {
        val windowState = rememberWindowState(
            size = DpSize(340.dp, 220.dp),
            position = WindowPosition(Alignment.TopEnd),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Countdown",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            CountdownApp(initialTarget = initialTarget, onClose = ::exitApplication)
        }
    }
}

@Composable
private fun FrameWindowScope.CountdownApp(initialTarget: LocalDateTime, onClose: () -> Unit) {
    var target by remember { mutableStateOf(initialTarget) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var persistenceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }

    val remaining = remainingTo(target, now)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B7CFF),
            surface = Color(0xFF1C1B22),
            onSurface = Color(0xFFEDECF4),
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF21C1B22),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                WindowDraggableArea {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (remaining.past) "TIME'S UP" else "COUNTDOWN",
                            color = Color(0xFFB9B4E0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        GlyphButton(glyph = "\u2699") {
                            draft = target.format(DISPLAY_FORMAT)
                            invalid = false
                            editing = !editing
                        }
                        GlyphButton(glyph = "\u2715", onClick = onClose)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (editing) {
                    EditPanel(
                        draft = draft,
                        invalid = invalid,
                        onDraftChange = {
                            draft = it
                            invalid = false
                        },
                        onCancel = { editing = false },
                        onApply = {
                            val parsed = parseTarget(draft)
                            if (parsed == null) {
                                invalid = true
                            } else {
                                target = parsed
                                persistenceError = saveTarget(parsed)
                                editing = false
                            }
                        },
                    )
                } else {
                    CountdownDisplay(
                        remaining = remaining,
                        target = target,
                        persistenceError = persistenceError,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownDisplay(
    remaining: Remaining,
    target: LocalDateTime,
    persistenceError: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TimeCell(value = remaining.days, label = "DAYS", pad = false)
        TimeCell(value = remaining.hours, label = "HRS")
        TimeCell(value = remaining.minutes, label = "MIN")
        TimeCell(value = remaining.seconds, label = "SEC")
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = (if (remaining.past) "since " else "until ") + target.format(DISPLAY_FORMAT),
        color = Color(0xFF9A96AD),
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    if (persistenceError != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = persistenceError,
            color = MaterialTheme.colorScheme.error,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimeCell(value: Long, label: String, pad: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp),
    ) {
        Text(
            text = if (pad) value.toString().padStart(2, '0') else value.toString(),
            color = Color(0xFFF3F1FB),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color(0xFF908CA6),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EditPanel(
    draft: String,
    invalid: Boolean,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            singleLine = true,
            isError = invalid,
            label = { Text(if (invalid) "Invalid \u2014 try yyyy-MM-dd HH:mm" else "Target date & time") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                Text("Set")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = Color(0xFFCFCBE6), fontSize = 16.sp)
    }
}
