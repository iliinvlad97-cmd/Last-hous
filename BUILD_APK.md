# APK с постоянной подписью

## Причина проблемы и совместимость старых установок

Раньше workflow выполнял `gradle assembleDebug` на новом временном GitHub
runner без постоянного signingConfig. Android Gradle Plugin создавал новый
случайный debug-keystore. Одинаковое имя сертификата `Android Debug` не
означает одинаковый ключ: SHA-256 fingerprint у таких сборок различается.
Android запрещает обновлять `com.lastdom.game` APK с несовместимой подписью
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Предыдущий APK не предоставлен,
поэтому конкретные два сертификата пока не сравнивались; источник смены
ключей установлен по workflow.

Раньше versionCode также всегда был 95. Одинаковый код обычно допускает
переустановку, но меньший вызывает `INSTALL_FAILED_VERSION_DOWNGRADE`.
Изменение versionName не исправляет несовпадение подписей или downgrade.

Для обновления уже установленной игры без удаления нужен **тот же приватный
ключ**, которым подписан её APK. Если старый keystore сохранился, используйте
его для Secrets ниже. Из APK можно извлечь публичный сертификат, но нельзя
восстановить приватный signing key.

Если старый ключ остался только на завершённом временном runner, стандартный
workflow его не сохранил. Новый постоянный ключ обеспечивает обновления
**между будущими сборками**, но не обновление старой установки с другим
сертификатом. Не удаляйте установленную игру ради проверки: её сохранения
важны. Этот патч не добавляет экспорт или миграцию данных между
несовместимыми подписями.

## Однократная настройка GitHub Actions Secrets

1. Используйте прежний keystore, если он сохранился. Если его нет, создайте
   **один** новый ключ на своём компьютере в отдельном каталоге **вне
   репозитория**. Нужен JDK с командой `keytool`. Не создавайте ключ заново
   для каждого APK.

   В этом отдельном каталоге выполните:

   ```text
   keytool -genkeypair -v -keystore last-house.jks -storetype JKS -alias lasthouse -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname "CN=Last House"
   ```

   Введите пароль хранилища интерактивно. Запишите пароль ключа; если при
   запросе пароля ключа нажали Enter, он совпадает с паролем хранилища.
   Не передавайте пароли в командах и не публикуйте их в чате.

2. Сохраните резервную копию keystore, alias и обоих паролей в защищённом
   месте. Потеря ключа снова нарушит совместимость обновлений.

3. Преобразуйте keystore в Base64-файл в том же отдельном каталоге.

   Linux/macOS/Git Bash:

   ```bash
   base64 < last-house.jks | tr -d '\r\n' > last-house.jks.base64
   ```

   Windows PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\last-house.jks))) | Set-Content -Encoding ascii -NoNewline .\last-house.jks.base64
   ```

   Base64 не шифрует ключ; этот файл тоже является секретом.

4. Откройте **iliinvlad97-cmd/Last-hous → Settings → Secrets and variables →
   Actions → New repository secret**:
   <https://github.com/iliinvlad97-cmd/Last-hous/settings/secrets/actions>.

5. Создайте четыре **Repository secrets** с точными именами:

   | Secret | Значение |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | Всё содержимое `last-house.jks.base64` |
   | `ANDROID_KEYSTORE_PASSWORD` | Пароль keystore |
   | `ANDROID_KEY_ALIAS` | `lasthouse` для примера выше; для старого ключа — его существующий alias |
   | `ANDROID_KEY_PASSWORD` | Пароль приватного ключа; может совпадать с паролем keystore |

   Значения вводятся только в GitHub Secrets. Keystore, его Base64-копию и
   пароли не добавляйте в репозиторий. `.gitignore` исключает распространённые
   файлы ключей и их Base64-копии.

6. После размещения изменений workflow на GitHub откройте **Actions → Build
   Last House APK → Run workflow → main**. При отсутствии любого Secret
   сборка остановится; случайный debug-ключ как запасной вариант не используется.

7. Скачайте artifact `LastHouse-debug-apk-<versionCode>`. Внутри будут
   `app-debug.apk`, `apk-identity.txt`, `apk-signing.txt`. Последний содержит
   публичный сертификат и SHA-256 fingerprint, а не приватный ключ.

8. Для следующих версий запускайте новый workflow run и сохраняйте те же
   Secrets. Устанавливайте APK поверх версии, подписанной этим же ключом.

## Нумерация и локальная сборка

- applicationId и namespace: **com.lastdom.game**.
- Текущий versionName: **0.9.9**.
- Локальный versionCode: **102**, увеличен с 101.
- GitHub Actions versionCode: **1000 + github.run_number**. Каждый новый
  запуск этого workflow получает больший код, чем предыдущий и старые APK с 95.
- **Re-run jobs** пересобирает ту же версию с тем же кодом. Для следующей
  версии используйте **Run workflow** или новый push.
- Не пересоздавайте workflow со сбросом счётчика. При изменении схемы
  нумерации новый код должен превышать максимальный уже выпущенный.
- Не устанавливайте локальный APK с кодом 102 поверх CI-версии с большим кодом.

Для локального APK, предназначенного для обновления CI-версии, используйте
тот же keystore и задайте четыре переменные окружения процесса:

| Переменная | Значение |
| --- | --- |
| `LAST_HOUSE_KEYSTORE_PATH` | Путь к постоянному keystore вне репозитория |
| `LAST_HOUSE_STORE_PASSWORD` | Пароль хранилища |
| `LAST_HOUSE_KEY_ALIAS` | Alias |
| `LAST_HOUSE_KEY_PASSWORD` | Пароль ключа |

Дополнительно задайте `LAST_HOUSE_REQUIRE_SIGNING=true`, чтобы исключить
случайный локальный debug-ключ. Передавайте пароли через защищённую среду
процесса, а не через файлы проекта. Затем выполните `gradle assembleDebug`
с параметром `-PlastHouseVersionCode=<новый числовой код>` больше ранее выпущенного.
Локальный код в исходниках также нужно увеличивать при следующих версиях.

Без переменных обычная локальная разработка сохраняет стандартную
Android debug-подпись; такой APK не является обновлением CI-подписи.
При настроенном keystore debug и release используют один постоянный ключ.

## Проверка подписи и метаданных

Для старого и нового APK выполните отдельно (Android SDK build-tools):

```bash
apksigner verify --print-certs old.apk
apksigner verify --print-certs new.apk
aapt2 dump badging new.apk
```

Сравните **Signer #1 certificate SHA-256 digest**, applicationId и versionCode.
Имя сертификата/alias само по себе не подтверждает совпадение ключей.
Workflow проверяет APK до загрузки artifact и удаляет временный keystore
даже при ошибке. Приватные ключи не публикуются.

Настройка подписи не меняет `save_v02`, его ключи, механику или ресурсы игры.
Сохранения остаются на месте при успешном обновлении с совместимой подписью.
