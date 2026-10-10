# SugarBridge ✧

Сахар с датчика FreeStyle Libre — в Samsung Health. Без своего сервера: приложение само забирает
замеры из облака LibreLinkUp и пишет их в Health Connect, а оттуда их берёт Samsung Health.

<p>
  <img src="docs/screenshots/home.png" width="190" alt="Главный экран">
  <img src="docs/screenshots/history.png" width="190" alt="История из LibreView">
  <img src="docs/screenshots/settings.png" width="190" alt="Настройки">
  <img src="docs/screenshots/notification.png" width="190" alt="Сахар в уведомлении">
  <img src="docs/screenshots/home_night.png" width="190" alt="Ночная тема">
</p>

> **Это не медицинское изделие.** Тревоги о низком и высоком сахаре и решения о лечении — только по
> официальному приложению LibreLink. SugarBridge работает через неофициальный API LibreLinkUp, который
> Abbott может поменять без предупреждения. Проект не связан с Abbott, Samsung и Google.

## Что умеет

- **Сахар в Samsung Health** — в фоне раз в 15 минут (можно реже) без уведомления в шторке.
- **Минутный режим** — каждый новый замер попадает в Samsung Health, а сахар со стрелкой виден
  числом в строке состояния. Уведомление можно сделать тихим или скрыть совсем.
- **Вся история из LibreView** — скачайте выгрузку прямо в приложении, выберите период и дни, которые
  нужны в Samsung Health. Повторы не задваиваются, ненужное можно удалить.
- **Свой график и статистика** за 12 часов, ммоль/л или мг/дл, свой целевой диапазон, ночная тема.
- **Пароль под защитой Android Keystore**, хранится только на телефоне.

Путь данных: датчик → LibreLink на телефоне → облако LibreLinkUp → SugarBridge → Health Connect →
Samsung Health.

## Совместимость

SugarBridge не подключается к датчику сам — он берёт то, что приложение с датчиком отправило в облако.
Поэтому подходит любой датчик, чьи замеры видны в LibreLinkUp.

| | |
|---|---|
| **Датчики** | FreeStyle Libre 2 и 2 Plus, Libre 3 и 3 Plus — новый замер каждую минуту. FreeStyle Libre (первый) — только после сканирования: история за 8 часов приходит при каждом скане, минутный режим для него бесполезен |
| **Приложение с датчиком** | FreeStyle LibreLink любой страны (в том числе российское), FreeStyle Libre 3, Libre (США) |
| **Наблюдение** | LibreLinkUp, в России — LibreLinkUp-RU |
| **Телефон** | Android 14 и новее |

**Россия и остальной мир.** Российский LibreView — отдельная система Abbott со своим сервером
(libreview.ru), у остального мира — libreview.io с регионами (Европа, США, Германия, Франция…).
SugarBridge сначала пробует сервер по стране телефона, потом второй, а регион запоминает после первого
входа — ничего настраивать не нужно. Окно истории открывает libreview.ru или libreview.com по
региону аккаунта.

## Скачать

**[⬇ SugarBridge.apk — последняя версия](../../releases/latest/download/SugarBridge.apk)** ·
[все версии](../../releases)

Что нужно:
- телефон на **Android 14** или новее (там Health Connect уже встроен в систему);
- приложение **LibreLink** с датчиком FreeStyle Libre и подключённым **LibreLinkUp** (см. ниже);
- **Samsung Health** — чтобы смотреть графики там. Без него данные всё равно будут в Health Connect.

### Установка

1. Откройте ссылку выше на телефоне и скачайте `SugarBridge.apk`.
2. Откройте скачанный файл. Android попросит разрешить установку из браузера или «Моих файлов» — разрешите.
3. Если Play Защита предупредит о неизвестном приложении, выберите «Всё равно установить».
4. **Обновление:** скачайте новую версию по той же ссылке и установите поверх — вход и настройки сохранятся.

> Если раньше вы ставили SugarBridge, собранный в Android Studio, сначала удалите его: у версий из
> Releases другая подпись, и поверх Android их не поставит.

### Первая настройка

1. **Аккаунт LibreLinkUp.** В LibreLink (или приложении Libre 3): меню → «Подключенные приложения» →
   LibreLinkUp → добавить подключение и указать **второй** email. Установите LibreLinkUp (в России —
   LibreLinkUp-RU), зарегистрируйтесь на этот email, примите приглашение и все условия. После этого
   само приложение LibreLinkUp можно удалить.
2. **Вход.** Откройте SugarBridge и войдите email и паролем этого аккаунта LibreLinkUp.
3. **Health Connect.** На главном экране нажмите «Разрешить» и включите запись уровня глюкозы.
4. **Samsung Health.** Настройки → Health Connect → разрешите Samsung Health **читать** уровень глюкозы.
5. По желанию: **Настройки → Минутный режим** и «Снять ограничения батареи» — иначе Samsung может
   усыплять приложение.

### Вся история из LibreView

LibreLinkUp помнит только последние 12 часов. Всё остальное хранится в LibreView:

1. Вкладка «История» → «Открыть LibreView».
2. Войдите аккаунтом **LibreLink** — тем, что в приложении с датчиком, а не LibreLinkUp.
3. «История глюкозы» → «Загрузить данные о глюкозе» → «Я не робот» → «Загрузить».
   SugarBridge сам заберёт файл.
4. Выберите период и дни → «Записать в Health Connect». Дни, которые уже есть, отмечены «✓ в HC».

CSV, скачанный в браузере, можно открыть через SugarBridge или выбрать кнопкой «Выбрать скачанный CSV».

## Частые вопросы

**Как часто обновляется сахар?** LibreLinkUp получает новое значение примерно раз в минуту, а его
история за 12 часов — это точки каждые 15 минут. В фоне SugarBridge забирает всю историю, поэтому
пропусков нет, но точки идут раз в 15 минут. Каждую минуту — только в минутном режиме. Насколько часто
LibreLinkUp на самом деле присылает значения, видно на главном экране.

**Почему в минутном режиме есть уведомление?** Чаще раза в 15 минут Android разрешает работать в фоне
только сервису с уведомлением. Его можно сделать тихим (Настройки → «Сахар в уведомлении») или
выключить совсем (кнопка «Скрыть уведомление совсем») — режим продолжит работать.

**Можно ли без Health Connect?** Нет, и не нужно: на Android 14+ это часть системы, а не отдельное
приложение. Писать в Samsung Health напрямую можно только с ключом партнёра Samsung. Если
Health Connect выключен или устарел, SugarBridge покажет, как это исправить.

**А брать сахар прямо из LibreLink, без облака?** LibreLink никому не отдаёт данные, а подключиться
к датчику напрямую значит отобрать его у LibreLink вместе с тревогами и выгрузкой в облако. Минутный
режим через LibreLinkUp даёт тот же результат безопасно.

**Куда уходит пароль?** Только в LibreLinkUp. На телефоне он зашифрован ключом Android Keystore,
который нельзя достать из телефона. Своего сервера, аналитики и рекламы у SugarBridge нет.

Нашли ошибку? [Заведите issue](../../issues/new?template=bug_report.md): шаги, что ожидали, что
получилось, кусок Logcat по тегу `SugarSync`.

## Для разработчиков

### Сборка и тесты

Нужны Android Studio (с ней идёт JDK) и Android SDK 37.

```sh
./gradlew assembleDebug             # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest         # unit-тесты: поддельный LibreLinkUp, разбор CSV, логика синхронизации
./gradlew lintDebug                 # Android Lint
./gradlew connectedDebugAndroidTest # на телефоне или эмуляторе Android 14+: Health Connect, вся цепочка, скриншоты
```

На Windows — `gradlew.bat`. CI на GitHub гоняет unit-тесты, Lint и сборку на каждый push.

### Устройство проекта

```
app/src/main/java/com/sugarbridge/app/
├── MainActivity.kt, MainViewModel.kt  экран и его состояние
├── glucose/     замер, единицы, целевой диапазон
├── libre/       клиент LibreLinkUp, разбор ответов, аккаунт под Keystore
├── libreview/   импорт истории: разбор CSV, окно сайта, сводка по дням
├── health/      Health Connect: запись, чтение своих записей, удаление
├── sync/        синхронизация, фоновая задача, минутный сервис, автозапуск
├── settings/    настройки
└── ui/          theme — оформление, components — карточки, график, ангелочек, screens — экраны
app/src/test/         unit-тесты, разложены так же
app/src/androidTest/  тесты на устройстве
```

### Выпуск новой версии

APK в Releases собирает и подписывает GitHub Actions, когда в репозиторий приходит тег `v*`.

1. **Один раз — ключ подписи.** Им подписываются все версии, без него обновить приложение не выйдет.
   Храните файл и пароли в менеджере паролей, **не в репозитории**:
   ```sh
   keytool -genkeypair -keystore sugarbridge.jks -alias sugarbridge -keyalg RSA -keysize 4096 -validity 36500
   ```
2. **Один раз — секреты репозитория** (Settings → Secrets and variables → Actions):
   `KEYSTORE_BASE64` — файл ключа в base64, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`sugarbridge`), `KEY_PASSWORD`.
   Base64 в PowerShell сразу в буфер обмена:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("sugarbridge.jks")) | Set-Clipboard
   ```
3. **Каждый выпуск:** допишите раздел версии в [CHANGELOG.md](CHANGELOG.md), затем
   ```sh
   git tag v1.2.0
   git push origin v1.2.0
   ```
   Через несколько минут в Releases появится `SugarBridge.apk` с описанием из CHANGELOG.

Подписанный APK можно собрать и у себя: пропишите в `~/.gradle/gradle.properties` (вне репозитория)
`sugarbridge.keystore`, `sugarbridge.keystorePassword`, `sugarbridge.keyAlias`, `sugarbridge.keyPassword`
и запустите `./gradlew assembleRelease -Psugarbridge.version=1.2.0`.

## Благодарности

- Гайд «Libre 2 → Samsung Health без сервера», с которого начался проект.
- Описания неофициального API: [pylibrelinkup](https://github.com/robberwick/pylibrelinkup),
  [libreview-unofficial](https://github.com/FokkeZB/libreview-unofficial),
  [HTTP-дамп LibreLinkUp](https://gist.github.com/khskekec/6c13ba01b10d3018d816706a32ae8ab2).
- Шрифт [Nunito](https://github.com/googlefonts/nunito) — SIL Open Font License, см. [licenses/](licenses/).

## Лицензия

[MIT](LICENSE). Шрифт Nunito распространяется по своей лицензии OFL. FreeStyle, Libre, LibreLink,
LibreLinkUp и LibreView — товарные знаки Abbott; Samsung Health — Samsung; Health Connect — Google.
