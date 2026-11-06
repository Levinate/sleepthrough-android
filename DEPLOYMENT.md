# Раздача APK на Railway

Репозиторий: [Levinate/sleepthrough-android](https://github.com/Levinate/sleepthrough-android). Публичная страница: https://sleepthrough-android-production.up.railway.app . Отдельный Railway-сервис `sleepthrough-android` в проекте SleepThrough, источник — ветка `main` этого репозитория. Сайт работает в другом сервисе `sleepthrough`.

APK 0.6.0-pilot: 14 914 598 байта. SHA-256: `4d7d23b670248c8a2ba5b956bfa8760ea121542020a87e895263e58cd476820d`. Публичный файл скачан после деплоя, размер и контрольная сумма совпали с локальным артефактом и GitHub Release.

Dockerfile запускает `node distribution-server.cjs`. Сервер слушает `HOST=0.0.0.0` и `PORT`, healthcheck `/health`. SDK, Java и сборка приложения в Railway не требуются.

Публичный сервис отдаёт страницу установки `/`, бинарник `/app.apk` или `/SleepThrough-0.6.0-pilot.apk`, контрольную сумму `/sha256.txt`. Поддерживаются HEAD и byte Range. Исходники сервера и приложения по HTTP не публикуются.

При обновлении собрать и проверить новый APK с нужной подписью, обновить файл в `releases/`, номер версии в сервере/README и GitHub Release, затем push. Не добавлять keystore, пароли, локальные SDK-пути и пользовательские записи. На другой машине debug key отличается: для обновления старой установки требуется прежний сертификат.
