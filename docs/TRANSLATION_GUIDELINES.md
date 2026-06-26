# Translation Guidelines

This document explains how to add or update translations for `vFlow`.

## Translation Files

Android string resources live under:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/strings_module.xml`

Each language should provide the same pair of files in its own resource directory, for example:

- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values-en/strings_module.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-ja/strings_module.xml`

If a translation is missing, Android falls back to the default `values/` resources.

## Adding A New Language

1. Create a new resource directory such as `values-ja` or `values-fr`.
2. Add `strings.xml` and `strings_module.xml`.
3. Copy the keys from the default `values/` files and translate only the text content.
4. Register the new language in [LocaleManager.kt](app/src/main/java/com/chaomixian/vflow/core/locale/LocaleManager.kt:29) by adding it to `SUPPORTED_LANGUAGES`.

Example:

```kotlin
val SUPPORTED_LANGUAGES = mapOf(
    "zh" to "中文（简体）",
    "en" to "English",
    "ja" to "日本語"
)
```

## Rules

- Do not change string `name` values.
- Do not remove strings that already exist in the default files.
- Keep placeholders exactly the same, such as `%s`, `%d`, `%.1f`, and their order.
- Keep escaped characters valid, such as `\n`, `\'`, and `\"`.
- Do not translate entries marked with `translatable="false"`.
- Preserve XML structure and formatting attributes such as `formatted="false"`.

## Style

- Prefer natural UI wording over literal word-for-word translation.
- Keep labels and button text short.
- Keep terminology consistent across similar modules and settings.
- When a string is unclear, check where it is used before translating it.

## Quick Check Before Opening A PR

- The new language files contain the same keys as the default files.
- Placeholders and escapes are unchanged.
- The app builds successfully.
- The language appears in Settings and can be switched normally.

## Notes For Contributors

- `strings.xml` mostly contains app UI text.
- `strings_module.xml` mostly contains module names, summaries, logs, and execution messages.
- If you are unsure about a phrase, open a draft PR and leave a note on the specific string.
