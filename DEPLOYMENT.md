# Раздача APK на Railway

Репозиторий: [Levinate/sleepthrough-android](https://github.com/Levinate/sleepthrough-android). Публичная страница: https://sleepthrough-android-production.up.railway.app . Отдельный Railway-сервис `sleepthrough-android` в проекте SleepThrough, источник — ветка `main` этого репозитория. Сайт работает в другом сервисе `sleepthrough`.

APK 0.6.1-pilot: 14 911 207 байта. SHA-256: `4e32ad87443b4c2dea0a0360499b272f4bccc5c53bc606d34a263157ab1deac0`. После деплоя сверьте публичный файл и /sha256.txt с контрольной суммой релиза.

Dockerfile запускает `node distribution-server.cjs`. Сервер слушает `HOST=0.0.0.0` и `PORT`, healthcheck `/health`. SDK, Java и сборка приложения в Railway не требуются.

Публичный сервис отдаёт страницу установки `/`, бинарник `/app.apk` или `/SleepThrough-0.6.1-pilot.apk`, контрольную сумму `/sha256.txt`. Поддерживаются HEAD и byte Range. Исходники сервера и приложения по HTTP не публикуются.

При обновлении собрать и проверить новый APK с нужной подписью, обновить файл в `releases/`, номер версии в сервере/README и GitHub Release, затем push. Не добавлять keystore, пароли, локальные SDK-пути и пользовательские записи. На другой машине debug key отличается: для обновления старой установки требуется прежний сертификат.
