package com.opentasker.core.actions

import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.VariableNamePolicy

/** Value kinds exposed by an action for downstream template inputs. */
enum class ActionValueType {
    ANY,
    TEXT,
    NUMBER,
    BOOLEAN,
    JSON,
    ARRAY,
}

/** Storage namespace used when an output is rendered as a template reference. */
enum class ActionOutputScope {
    INFERRED,
    GLOBAL,
}

/** A stable, metadata-owned declaration of one variable an action may produce. */
data class ActionOutputDefinition(
    val key: String,
    val type: ActionValueType,
    val nameArgumentKeys: List<String> = emptyList(),
    val defaultName: String? = null,
    val suffix: String = "",
    val scope: ActionOutputScope = ActionOutputScope.INFERRED,
    val omitWhenArgumentsPresent: List<String> = emptyList(),
    val basePath: Boolean = false,
    val requiredArgumentValues: Map<String, Set<String>> = emptyMap(),
) {
    fun resolveName(args: Map<String, String>): String? {
        if (omitWhenArgumentsPresent.any { !args[it].isNullOrBlank() }) return null
        if (requiredArgumentValues.any { (key, allowed) ->
                args[key]?.trim()?.lowercase() !in allowed.map(String::lowercase)
            }) return null
        val rawName = nameArgumentKeys
            .asSequence()
            .mapNotNull { args[it] }
            .firstOrNull { it.isNotBlank() }
            ?: defaultName
            ?: return null
        val candidate = rawName.trim().let { raw ->
            if (!basePath) raw else raw.substringBefore('.').substringBefore('[')
        }
        val normalizedBase = VariableNamePolicy.normalize(candidate) ?: return null
        return if (scope == ActionOutputScope.GLOBAL) {
            VariableNamePolicy.promoteToGlobal(normalizedBase + suffix)
        } else {
            VariableNamePolicy.normalize(normalizedBase + suffix)
        }
    }
}

/** A resolved output with enough provenance for the editor to label it as a step chip. */
data class ResolvedActionOutput(
    val key: String,
    val name: String,
    val type: ActionValueType,
    val scope: ActionOutputScope,
    val actionType: String,
    val actionIndex: Int? = null,
) {
    /** Canonical template text. It is deliberately ordinary text in [ActionSpec.args]. */
    val reference: String
        get() = when {
            type == ActionValueType.ARRAY -> "{{ array.$name }}"
            scope == ActionOutputScope.GLOBAL ||
                (scope == ActionOutputScope.INFERRED && VariableNamePolicy.isGlobal(name)) -> {
                "{{ global.$name }}"
            }
            else -> "{{ $name }}"
        }
}

fun ActionMetadata.resolveOutputs(
    action: ActionSpec,
    actionIndex: Int? = null,
): List<ResolvedActionOutput> = outputs.mapNotNull { definition ->
    definition.resolveName(action.args)?.let { name ->
        ResolvedActionOutput(
            key = definition.key,
            name = name,
            type = definition.type,
            scope = definition.scope,
            actionType = action.type,
            actionIndex = actionIndex,
        )
    }
}

enum class VariableChipScope {
    STEP,
    EVENT,
    GLOBAL,
    SYSTEM,
    FLOW_ERROR,
}

/** A value-only editor option. Secret values are intentionally not part of this type. */
data class VariableChipOption(
    val name: String,
    val type: ActionValueType,
    val scope: VariableChipScope,
    val reference: String,
    val actionIndex: Int? = null,
    val actionType: String? = null,
    val description: String = "",
)

data class EditorEventVariable(
    val name: String,
    val type: ActionValueType,
)

/** Event fields currently made available to task execution by the share receiver. */
val DEFAULT_EDITOR_EVENT_VARIABLES: List<EditorEventVariable> = listOf(
    EditorEventVariable("share_event", ActionValueType.BOOLEAN),
    EditorEventVariable("share_text", ActionValueType.TEXT),
    EditorEventVariable("share_uri", ActionValueType.TEXT),
    EditorEventVariable("share_uris", ActionValueType.TEXT),
    EditorEventVariable("share_mime", ActionValueType.TEXT),
    EditorEventVariable("share_count", ActionValueType.NUMBER),
    EditorEventVariable("share_multiple", ActionValueType.BOOLEAN),
)

data class SystemEditorVariable(
    val name: String,
    val type: ActionValueType,
    val description: String,
)

data class FlowEditorVariable(
    val name: String,
    val type: ActionValueType,
    val description: String,
)

/** Error details made available inside flow.catch and action fallback runs. */
val DEFAULT_EDITOR_FLOW_VARIABLES: List<FlowEditorVariable> = listOf(
    FlowEditorVariable("FLOW_ERROR_JSON", ActionValueType.JSON, "Structured details about the failed action."),
    FlowEditorVariable("FLOW_ERROR_TASK_ID", ActionValueType.NUMBER, "ID of the task that failed."),
    FlowEditorVariable("FLOW_ERROR_TASK_NAME", ActionValueType.TEXT, "Name of the task that failed."),
    FlowEditorVariable("FLOW_ERROR_ACTION_ID", ActionValueType.NUMBER, "ID of the failed action block."),
    FlowEditorVariable("FLOW_ERROR_ACTION", ActionValueType.TEXT, "Type of the failed action."),
    FlowEditorVariable("FLOW_ERROR_INDEX", ActionValueType.NUMBER, "One-based position of the failed action."),
    FlowEditorVariable("FLOW_ERROR_TYPE", ActionValueType.TEXT, "Action type that failed."),
    FlowEditorVariable("FLOW_ERROR_MESSAGE", ActionValueType.TEXT, "Failure message from the action."),
    FlowEditorVariable("FLOW_ERROR_ATTEMPT", ActionValueType.NUMBER, "Number of attempts made by the failed action."),
    FlowEditorVariable("FLOW_ERROR_RETRYING", ActionValueType.BOOLEAN, "Whether the action is currently being retried."),
    FlowEditorVariable("FLOW_ERROR_RETRY_REASON", ActionValueType.TEXT, "Reason recorded for an action retry."),
    FlowEditorVariable("FLOW_ERROR_PROFILE_ID", ActionValueType.NUMBER, "ID of the profile that started the task, when available."),
    FlowEditorVariable("FLOW_ERROR_PROFILE_NAME", ActionValueType.TEXT, "Name of the profile that started the task, when available."),
    FlowEditorVariable("FLOW_ERROR_CAUGHT", ActionValueType.BOOLEAN, "Whether the failure has been handled by flow.catch."),
)

/** Live device values captured at the start of every automation run. */
val DEFAULT_EDITOR_SYSTEM_VARIABLES: List<SystemEditorVariable> = listOf(
    SystemEditorVariable("brightness", ActionValueType.NUMBER, "Screen brightness (0–255)."),
    SystemEditorVariable("brightness_percent", ActionValueType.NUMBER, "Screen brightness as a percentage."),
    SystemEditorVariable("brightness_mode", ActionValueType.TEXT, "Automatic or manual brightness mode."),
    SystemEditorVariable("brightness_automatic", ActionValueType.BOOLEAN, "Whether automatic brightness is enabled."),
    SystemEditorVariable("volume", ActionValueType.NUMBER, "Current media volume level."),
    SystemEditorVariable("volume_max", ActionValueType.NUMBER, "Maximum media volume level."),
    SystemEditorVariable("volume_percent", ActionValueType.NUMBER, "Media volume as a percentage."),
    SystemEditorVariable("volume_muted", ActionValueType.BOOLEAN, "Whether media volume is muted."),
    SystemEditorVariable("volume_ring", ActionValueType.NUMBER, "Current ringtone volume level."),
    SystemEditorVariable("volume_ring_max", ActionValueType.NUMBER, "Maximum ringtone volume level."),
    SystemEditorVariable("volume_ring_percent", ActionValueType.NUMBER, "Ringtone volume as a percentage."),
    SystemEditorVariable("volume_alarm", ActionValueType.NUMBER, "Current alarm volume level."),
    SystemEditorVariable("volume_alarm_max", ActionValueType.NUMBER, "Maximum alarm volume level."),
    SystemEditorVariable("volume_alarm_percent", ActionValueType.NUMBER, "Alarm volume as a percentage."),
    SystemEditorVariable("volume_notification", ActionValueType.NUMBER, "Current notification volume level."),
    SystemEditorVariable("volume_notification_max", ActionValueType.NUMBER, "Maximum notification volume level."),
    SystemEditorVariable("volume_notification_percent", ActionValueType.NUMBER, "Notification volume as a percentage."),
    SystemEditorVariable("battery_percent", ActionValueType.NUMBER, "Current battery charge percentage."),
    SystemEditorVariable("battery_level", ActionValueType.NUMBER, "Raw battery charge level."),
    SystemEditorVariable("battery_scale", ActionValueType.NUMBER, "Maximum battery charge level reported by Android."),
    SystemEditorVariable("battery_charging", ActionValueType.BOOLEAN, "Whether the battery is charging or full."),
    SystemEditorVariable("battery_status", ActionValueType.TEXT, "Charging, discharging, full, not charging, or unknown."),
    SystemEditorVariable("battery_plugged", ActionValueType.TEXT, "AC, USB, wireless, dock, or unplugged."),
    SystemEditorVariable("battery_temperature_c", ActionValueType.NUMBER, "Battery temperature in degrees Celsius."),
    SystemEditorVariable("battery_voltage_mv", ActionValueType.NUMBER, "Battery voltage in millivolts."),
    SystemEditorVariable("wifi", ActionValueType.TEXT, "Current Wi-Fi connection name or disconnected."),
    SystemEditorVariable("wifi_ssid", ActionValueType.TEXT, "Connected Wi-Fi network name, when available."),
    SystemEditorVariable("wifi_connected", ActionValueType.BOOLEAN, "Whether Wi-Fi is connected."),
    SystemEditorVariable("internet", ActionValueType.BOOLEAN, "Whether the device currently has internet access."),
    SystemEditorVariable("network_type", ActionValueType.TEXT, "Current network type."),
    SystemEditorVariable("vpn", ActionValueType.BOOLEAN, "Whether a VPN is connected."),
)

/**
 * Returns all references visible while editing an action. The current action is excluded when it
 * is being edited, so a step cannot accidentally reference its own output. Global names are
 * included without their values, which keeps secret variables out of the UI data path.
 */
fun typedVariableOptions(
    actions: List<ActionSpec>,
    editingIndex: Int? = null,
    globalNames: Collection<String> = emptyList(),
    eventVariables: List<EditorEventVariable> = DEFAULT_EDITOR_EVENT_VARIABLES,
    systemVariables: List<SystemEditorVariable> = DEFAULT_EDITOR_SYSTEM_VARIABLES,
    flowVariables: List<FlowEditorVariable> = DEFAULT_EDITOR_FLOW_VARIABLES,
): List<VariableChipOption> {
    val precedingCount = editingIndex?.coerceIn(0, actions.size) ?: actions.size
    val stepOptions = actions.take(precedingCount).flatMapIndexed { index, action ->
        ActionMetadataRegistry.get(action.type)
            ?.resolveOutputs(action, actionIndex = index)
            .orEmpty()
            .map { output ->
                VariableChipOption(
                    name = output.name,
                    type = output.type,
                    scope = VariableChipScope.STEP,
                    reference = output.reference,
                    actionIndex = index,
                    actionType = action.type,
                )
            }
    }
    val eventOptions = eventVariables.map { event ->
        VariableChipOption(
            name = event.name,
            type = event.type,
            scope = VariableChipScope.EVENT,
            reference = "{{ event.${event.name} }}",
        )
    }
    val flowVariableNames = flowVariables.mapTo(mutableSetOf(), FlowEditorVariable::name)
    val globalOptions = globalNames
        .asSequence()
        .mapNotNull(VariableNamePolicy::normalize)
        .filterNot { it in flowVariableNames }
        .distinct()
        .map { name ->
            VariableChipOption(
                name = name,
                type = ActionValueType.ANY,
                scope = VariableChipScope.GLOBAL,
                reference = "{{ global.$name }}",
            )
        }
        .toList()
    val systemOptions = systemVariables
        .distinctBy(SystemEditorVariable::name)
        .map { variable ->
            VariableChipOption(
                name = variable.name,
                type = variable.type,
                scope = VariableChipScope.SYSTEM,
                reference = "{{ system.${variable.name} }}",
                description = variable.description,
            )
        }
    val flowOptions = flowVariables
        .distinctBy(FlowEditorVariable::name)
        .map { variable ->
            VariableChipOption(
                name = variable.name,
                type = variable.type,
                scope = VariableChipScope.FLOW_ERROR,
                reference = "{{ global.${variable.name} }}",
                description = variable.description,
            )
        }
    return stepOptions + eventOptions + globalOptions + systemOptions + flowOptions
}

/** Whether a metadata field can consume a declared output without an implicit type cast. */
fun ActionField.acceptsVariableType(type: ActionValueType): Boolean {
    inputType?.let { expected ->
        return type == ActionValueType.ANY || type == expected
    }
    return when (fieldType) {
        FieldType.TEXT,
        FieldType.MULTILINE,
        -> type in setOf(
            ActionValueType.ANY,
            ActionValueType.TEXT,
            ActionValueType.NUMBER,
            ActionValueType.BOOLEAN,
            ActionValueType.JSON,
        )
        FieldType.NUMBER -> type == ActionValueType.ANY || type == ActionValueType.NUMBER
        FieldType.CHECKBOX -> type == ActionValueType.ANY || type == ActionValueType.BOOLEAN
        FieldType.DROPDOWN,
        FieldType.TASK,
        FieldType.ACTION_BLOCK,
        FieldType.APP,
        FieldType.FILE,
        FieldType.SCENE,
        -> false
    }
}

/** Inserts a canonical template token while preserving an existing hand-authored expression. */
fun insertVariableChip(existing: String, option: VariableChipOption): String {
    if (existing.isBlank()) return option.reference
    val separator = if (existing.last().isWhitespace()) "" else " "
    return existing + separator + option.reference
}

/** Replaces the current text selection with a variable token and returns the new caret position. */
fun insertVariableAtSelection(
    existing: String,
    selectionStart: Int,
    selectionEnd: Int,
    option: VariableChipOption,
): Pair<String, Int> {
    val start = selectionStart.coerceIn(0, existing.length)
    val end = selectionEnd.coerceIn(start, existing.length)
    val updated = existing.replaceRange(start, end, option.reference)
    return updated to (start + option.reference.length)
}

private fun output(
    key: String,
    type: ActionValueType,
    nameArgumentKeys: List<String> = emptyList(),
    defaultName: String? = null,
    suffix: String = "",
    scope: ActionOutputScope = ActionOutputScope.INFERRED,
    omitWhenArgumentsPresent: List<String> = emptyList(),
    basePath: Boolean = false,
    requiredArgumentValues: Map<String, Set<String>> = emptyMap(),
) = ActionOutputDefinition(
    key = key,
    type = type,
    nameArgumentKeys = nameArgumentKeys,
    defaultName = defaultName,
    suffix = suffix,
    scope = scope,
    omitWhenArgumentsPresent = omitWhenArgumentsPresent,
    basePath = basePath,
    requiredArgumentValues = requiredArgumentValues,
)

/** Output declarations for the built-in actions whose runtime writes variables. */
internal fun declaredActionOutputs(actionId: String): List<ActionOutputDefinition> = when (actionId) {
    "var.set" -> listOf(output("value", ActionValueType.ANY, nameArgumentKeys = listOf("name"), basePath = true))
    "var.persist" -> listOf(output("value", ActionValueType.ANY, nameArgumentKeys = listOf("global_name", "name"), scope = ActionOutputScope.GLOBAL))
    "brightness.read" -> listOf(
        output("level", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "brightness"),
        output("percent", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "brightness", suffix = "_percent"),
        output("mode", ActionValueType.TEXT, nameArgumentKeys = listOf("var"), defaultName = "brightness", suffix = "_mode"),
        output("automatic", ActionValueType.BOOLEAN, nameArgumentKeys = listOf("var"), defaultName = "brightness", suffix = "_automatic"),
    )
    "volume.read" -> listOf(
        output("level", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "volume"),
        output("max", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "volume", suffix = "_max"),
        output("percent", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "volume", suffix = "_percent"),
        output("muted", ActionValueType.BOOLEAN, nameArgumentKeys = listOf("var"), defaultName = "volume", suffix = "_muted"),
    )
    "torch.read" -> listOf(
        output("enabled", ActionValueType.BOOLEAN, nameArgumentKeys = listOf("var"), defaultName = "flashlight"),
        output("state", ActionValueType.TEXT, nameArgumentKeys = listOf("var"), defaultName = "flashlight", suffix = "_state"),
    )
    "data.read" -> listOf(
        output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "data"),
        output("array", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "variable"), defaultName = "data"),
        output("count", ActionValueType.NUMBER, nameArgumentKeys = listOf("var", "variable"), defaultName = "data", suffix = "_count"),
    )
    "datetime.format" -> listOf(output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "datetime"))
    "datetime.parse", "datetime.add" -> listOf(output("value", ActionValueType.NUMBER, nameArgumentKeys = listOf("var", "variable"), defaultName = "datetime"))
    "text.match" -> listOf(
        output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "match"),
        output("array", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "variable"), defaultName = "match"),
        output("count", ActionValueType.NUMBER, nameArgumentKeys = listOf("var", "variable"), defaultName = "match", suffix = "_count"),
    )
    "text.replace" -> listOf(output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "result"))
    "text.split" -> listOf(
        output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "parts"),
        output("array", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "variable"), defaultName = "parts"),
        output("count", ActionValueType.NUMBER, nameArgumentKeys = listOf("var", "variable"), defaultName = "parts", suffix = "_count"),
    )
    "text.join" -> listOf(output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "joined"))
    "text.substring" -> listOf(output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "substring"))
    "flow.foreach" -> listOf(output("item", ActionValueType.ANY, nameArgumentKeys = listOf("var"), defaultName = "item"))
    "intent.launch" -> listOf(output("result", ActionValueType.NUMBER, nameArgumentKeys = listOf("result_variable"), requiredArgumentValues = mapOf("mode" to setOf("broadcast"))))
    "plugin.locale.query" -> listOf(output("state", ActionValueType.TEXT, nameArgumentKeys = listOf("resultVariable")))
    "script.termux.run" -> listOf(
        output("stdout", ActionValueType.TEXT, nameArgumentKeys = listOf("capturePrefix"), suffix = "_stdout"),
        output("stderr", ActionValueType.TEXT, nameArgumentKeys = listOf("capturePrefix"), suffix = "_stderr"),
        output("exit_code", ActionValueType.NUMBER, nameArgumentKeys = listOf("capturePrefix"), suffix = "_exit_code"),
        output("stdout_length", ActionValueType.NUMBER, nameArgumentKeys = listOf("capturePrefix"), suffix = "_stdout_length"),
        output("stderr_length", ActionValueType.NUMBER, nameArgumentKeys = listOf("capturePrefix"), suffix = "_stderr_length"),
    )
    "file.read", "file.list" -> listOf(output("value", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "result"))
    "http.request" -> listOf(
        output("response", ActionValueType.TEXT, nameArgumentKeys = listOf("response_var"), defaultName = "result", omitWhenArgumentsPresent = listOf("output_file")),
        output("status", ActionValueType.NUMBER, nameArgumentKeys = listOf("status_var")),
        output("headers", ActionValueType.TEXT, nameArgumentKeys = listOf("headers_var")),
    )
    "http.get", "http.post" -> listOf(output("response", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "variable"), defaultName = "result"))
    "ping" -> listOf(output("reachable", ActionValueType.BOOLEAN, nameArgumentKeys = listOf("var"), defaultName = "result"))
    "ime.info" -> listOf(
        output("current", ActionValueType.TEXT, nameArgumentKeys = listOf("var"), defaultName = "IME", suffix = "_CURRENT"),
        output("enabled", ActionValueType.TEXT, nameArgumentKeys = listOf("var"), defaultName = "IME", suffix = "_ENABLED"),
        output("count", ActionValueType.NUMBER, nameArgumentKeys = listOf("var"), defaultName = "IME", suffix = "_COUNT"),
    )
    "clipboard.get" -> listOf(
        output("text", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "result"), defaultName = "Clipboard"),
        output("has_text", ActionValueType.BOOLEAN, nameArgumentKeys = listOf("var", "result"), defaultName = "Clipboard", suffix = "_has_text"),
    )
    "contacts.lookup" -> listOf(
        output("id", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_id"),
        output("name", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_name"),
        output("phone", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_phone"),
        output("email", ActionValueType.TEXT, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_email"),
        output("count", ActionValueType.NUMBER, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_count"),
        output("names", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_names"),
        output("phones", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_phones"),
        output("emails", ActionValueType.ARRAY, nameArgumentKeys = listOf("var", "result"), defaultName = "Contact", suffix = "_emails"),
    )
    else -> emptyList()
}
