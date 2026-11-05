# Раздача APK на Railway

Репозиторий: [Levinate/sleepthrough-android](https://github.com/Levinate/sleepthrough-android). Публичная страница: https://sleepthrough-android-production.up.railway.app . Отдельный Railway-сервис `sleepthrough-android` в проекте SleepThrough, источник — ветка `main` этого репозитория. Сайт работает в другом сервисе `sleepthrough`.

APK 0.5.0-pilot: 15 084 144 байта. SHA-256: `927e8f10cd074233fe3fbf4e316bc9df147e705730c89158dcaddf5a9e792455`. Публичный файл скачан после деплоя, размер и контрольная сумма совпали с локальным артефактом и GitHub Release.

Dockerfile запускает `node distribution-server.cjs`. Сервер слушает `HOST=0.0.0.0` и `PORT`, healthcheck `/health`. SDK, Java и сборка приложения в Railway не требуются.

Публичный сервис отдаёт страницу установки `/`, бинарник `/app.apk` или `/SleepThrough-0.5.0-pilot.apk`, контрольную сумму `/sha256.txt`. Поддерживаются HEAD и byte Range. Исходники сервера и приложения по HTTP не публикуются.

При обновлении собрать и проверить новый APK с нужной подписью, обновить файл в `releases/`, номер версии в сервере/README и GitHub Release, затем push. Не добавлять keystore, пароли, локальные SDK-пути и пользовательские записи. На другой машине debug key отличается: для обновления старой установки требуется прежний сертификат.
