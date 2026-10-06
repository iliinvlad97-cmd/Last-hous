# Как получить APK

Самый простой путь без установки Android Studio:

1. Создайте пустой репозиторий на GitHub.
2. Загрузите в него содержимое папки `LastHouse`.
3. Откройте вкладку **Actions**.
4. Запустите workflow **Build Last House APK**.
5. После завершения откройте готовый workflow и скачайте artifact **LastHouse-debug-apk**.
6. Внутри будет `app-debug.apk`, который можно перенести на Android-смартфон и установить.

Workflow сам устанавливает Android SDK, Gradle и собирает APK.
