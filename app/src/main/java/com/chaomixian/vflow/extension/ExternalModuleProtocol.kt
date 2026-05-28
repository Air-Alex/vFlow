package com.chaomixian.vflow.extension

import android.os.Bundle
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.PickerType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

object ExternalModuleProtocol {
    const val API_VERSION = 1
    const val ACTION_MODULE_PROVIDER = "com.chaomixian.vflow.extension.MODULE_PROVIDER"
    const val META_API_VERSION = "com.chaomixian.vflow.extension.API_VERSION"

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_MODULE_ID = "moduleId"
    const val KEY_PARAMETERS_JSON = "parametersJson"
    const val KEY_TIMEOUT_MS = "timeoutMs"
    const val KEY_LOCALE = "locale"

    const val KEY_STATUS = "status"
    const val KEY_OUTPUTS_JSON = "outputsJson"
    const val KEY_ERROR_TITLE = "errorTitle"
    const val KEY_ERROR_MESSAGE = "errorMessage"

    const val STATUS_SUCCESS = "success"
    const val STATUS_FAILURE = "failure"

    private val gson = Gson()

    fun parseManifest(bundle: Bundle): ProviderManifest {
        bundle.classLoader = ProviderManifest::class.java.classLoader
        val json = bundle.getString("manifestJson").orEmpty()
        require(json.isNotBlank()) { "Provider manifest is empty" }
        return gson.fromJson(json, ProviderManifest::class.java)
    }

    fun manifestBundle(manifest: ProviderManifest): Bundle =
        Bundle().apply { putString("manifestJson", gson.toJson(manifest)) }

    fun parametersJson(parameters: Map<String, Any?>): String = gson.toJson(parameters)

    @Suppress("UNCHECKED_CAST")
    fun parseMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) return emptyMap()
        return gson.fromJson(json, Map::class.java) as? Map<String, Any?> ?: emptyMap()
    }
}

data class ProviderManifest(
    val providerPackage: String,
    val providerName: String,
    val providerVersion: String,
    val apiVersion: Int = ExternalModuleProtocol.API_VERSION,
    val modules: List<ExternalModuleSpec> = emptyList()
)

data class ExternalModuleSpec(
    val moduleId: String,
    val moduleVersion: String,
    val name: String,
    val description: String,
    val categoryId: String = "external_extension",
    val iconUri: String? = null,
    val inputs: List<ExternalInputSpec> = emptyList(),
    val outputs: List<ExternalOutputSpec> = emptyList(),
    val requiredCapabilities: List<String> = emptyList()
)

data class ExternalInputSpec(
    val id: String,
    val name: String,
    val type: String,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList(),
    val acceptsMagicVariable: Boolean = true,
    val acceptsNamedVariable: Boolean = true,
    val supportsRichText: Boolean = false,
    val isHidden: Boolean = false,
    val isFolded: Boolean = false,
    @SerializedName("picker")
    val pickerType: String? = null
) {
    fun toInputDefinition(): InputDefinition =
        InputDefinition(
            id = id,
            name = name,
            staticType = toParameterType(type),
            defaultValue = defaultValue,
            options = options,
            acceptsMagicVariable = acceptsMagicVariable,
            acceptsNamedVariable = acceptsNamedVariable,
            supportsRichText = supportsRichText,
            isHidden = isHidden,
            isFolded = isFolded,
            pickerType = toPickerType(pickerType)
        )
}

data class ExternalOutputSpec(
    val id: String,
    val name: String,
    val type: String,
    val listElementType: String? = null
) {
    fun toOutputDefinition(): OutputDefinition =
        OutputDefinition(
            id = id,
            name = name,
            typeName = toTypeName(type),
            listElementType = listElementType
        )
}

private fun toParameterType(type: String): ParameterType =
    when (type.lowercase()) {
        "number" -> ParameterType.NUMBER
        "boolean" -> ParameterType.BOOLEAN
        "enum" -> ParameterType.ENUM
        "any", "dictionary", "list" -> ParameterType.ANY
        else -> ParameterType.STRING
    }

private fun toPickerType(type: String?): PickerType =
    when (type?.lowercase()) {
        "app" -> PickerType.APP
        "activity" -> PickerType.ACTIVITY
        "file" -> PickerType.FILE
        "directory" -> PickerType.DIRECTORY
        "media" -> PickerType.MEDIA
        "date" -> PickerType.DATE
        "time" -> PickerType.TIME
        "datetime" -> PickerType.DATETIME
        "screen_region" -> PickerType.SCREEN_REGION
        else -> PickerType.NONE
    }

private fun toTypeName(type: String): String =
    when (type.lowercase()) {
        "number" -> VTypeRegistry.NUMBER.id
        "boolean" -> VTypeRegistry.BOOLEAN.id
        "list" -> VTypeRegistry.LIST.id
        "dictionary" -> VTypeRegistry.DICTIONARY.id
        "image" -> VTypeRegistry.IMAGE.id
        "file" -> VTypeRegistry.FILE.id
        else -> VTypeRegistry.STRING.id
    }
