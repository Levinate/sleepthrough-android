# Стек Android

Capacitor 8.6.0 и Kotlin; JavaScript ES modules + HTML/CSS; esbuild 0.28.2; Supabase JS 2.119.0; Android SDK 36 / min SDK 28; JDK 21. Health Connect читает сон Samsung Health с отдельным разрешением. Android Keystore хранит ключ для шифрованного локального состояния. AlarmManager/NotificationCompat обеспечивают локальные напоминания.

33 теста проверяют хранение, повторный вход, изоляцию аккаунтов, отчёты Samsung + дневник, правила профиля и чек-ин. Нативные проверки выполнялись на Android 9/16. Подробности функций и сборки — в [README](README.md).

Railway в этом репозитории обслуживает только раздачу готового APK: Node.js 24, встроенный HTTP/fs/crypto и Docker. API-ключи оплаты ему не нужны. Исходники приложения собираются на машине с Android SDK; готовый APK публикуется в `releases/` и GitHub Releases.


## 0.6.0 cabinet

`src/access.mjs` owns route authorization, `src/cabinet.js` contains night support/check-ins/reminders, and `src/cabinet-theme.css` aligns the interface with the web design. `src/boot.js` checks the WebView generation before loading the bundle. Test data uses a separate encrypted namespace and never a shared cloud login. No payment SDK or quiz UI is shipped.
