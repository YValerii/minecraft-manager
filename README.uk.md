# Minecraft Manager

*Читати іншою мовою: [English](README.md)*

Застосунок, який показує все, що встановлено у вашому Minecraft, підтягує назви, іконки та версії з Modrinth (або CurseForge) і оновлює моди, ресурс паки, шейдери та датапаки однією кнопкою.

- **Backend:** Spring Boot 3.5, Java 21, налаштування та кеш розпізнавання модів зберігаються у локальних JSON-файлах (без бази даних)
- **Frontend:** Angular 21 (standalone, signals, zoneless)
- **Десктоп:** інсталятори Electron для Windows, macOS і Linux, Java вже всередині
- **Або запуск через:** Docker Compose, одна команда

## Завантаження

Встановлюється як звичайний застосунок. Java, Docker і база даних не потрібні. Завантажте інсталятор для своєї системи з [останнього релізу](https://github.com/YValerii/minecraft-manager/releases/latest):

| Система | Файл |
|---|---|
| Windows 10/11 | `minecraft-manager-<version>-win-x64.msi` або `minecraft-manager-<version>-win-x64.exe` |
| macOS, Apple Silicon | `minecraft-manager-<version>-mac-arm64.dmg` |
| macOS, Intel | `minecraft-manager-<version>-mac-x64.dmg` |
| Linux | `minecraft-manager-<version>-linux-x86_64.AppImage` або `minecraft-manager-<version>-linux-amd64.deb` |
| Будь-яка система з Java 21+ | `minecraft-manager-<version>.jar` |

Інсталятори поки не мають цифрового підпису, тому перший запуск потребує додаткового кроку:

- **Windows:** у попередженні SmartScreen виберіть «Докладніше», потім «Усе одно запустити».
- **macOS:** перетягніть застосунок до Applications, потім виконайте в Terminal `xattr -cr "/Applications/Minecraft Manager.app"`. Без цього macOS повідомить, що застосунок пошкоджено.
- **Linux AppImage:** спочатку виконайте для файлу `chmod +x`. В Ubuntu 24.04 і новіших також запускайте його з `--no-sandbox` або встановіть `.deb`.

Налаштування зберігаються в папці даних застосунку: `%APPDATA%\Minecraft Manager` у Windows, `~/Library/Application Support/Minecraft Manager` у macOS, `~/.config/Minecraft Manager` у Linux.

## Запуск jar-файлу

Jar містить і UI, і бекенд. Потрібна Java 21 або новіша, працює однаково на будь-якій ОС:

```bash
java -jar minecraft-manager-1.0.0.jar
```

Відкриється http://localhost:8080 у браузері. Якщо порт зайнятий, додайте `--server.port=9000`. Налаштування зберігаються в папці `data` у каталозі запуску; змінити її можна через `APP_DATA_DIR`. Щоб зупинити, натисніть Ctrl+C у терміналі. Застосунок доступний лише з вашого комп'ютера.

## Запуск через Docker

```bash
docker compose up --build
```

Відкрийте http://localhost:4200.

За замовчуванням у контейнер монтується `%APPDATA%\.minecraft` (Windows). На іншій ОС або з нестандартним розташуванням задайте папку в `.env` (шаблон у `.env.example`):

```env
MINECRAFT_DIR=C:/Users/Ім'я/AppData/Roaming/.minecraft
# Linux:  MINECRAFT_DIR=/home/user/.minecraft
# macOS:  MINECRAFT_DIR=/Users/user/Library/Application Support/minecraft
```

Папка монтується з правом запису, інакше оновлення неможливі.

## Можливості

| Вкладка | Що показує |
|---|---|
| Моди | `mods/*.jar` |
| Ресурс паки | `resourcepacks/*.zip` і папки |
| Датапаки | `datapacks/` та `saves/<світ>/datapacks/` (з позначкою світу) |
| Шейдери | `shaderpacks/*.zip` і папки |
| Світи | плитки з іконкою, назвою, версією гри; клік відкриває модалку з даними з `level.dat` |
| Налаштування | шлях до Minecraft, папки, версія гри, лоадер, джерело (Modrinth / CurseForge), ключ CurseForge |

Значення за замовчуванням: Minecraft **26.3**, лоадер **neoforge**, джерело **Modrinth**. Усе змінюється на сторінці «Налаштування».

### Як розпізнаються файли

Файл ідентифікується за хешем, а не за назвою. Для Modrinth використовується SHA-1, для CurseForge фінгерпринт murmur2. Тому назва файлу може бути будь-якою. З сайту береться коректна назва, опис, іконка та поточна версія. Результат кешується в локальному JSON-файлі (`app.data-dir/identity-cache.json`).

Якщо файл на сайті не знайдено (власна збірка, папка-пак), показуються дані, прочитані з самого файлу: `neoforge.mods.toml`, `mods.toml`, `fabric.mod.json`, `quilt.mod.json`, `mcmod.info`, `pack.mcmeta` та `pack.png`. Такий елемент позначається як «Не розпізнано».

### Оновлення

Оновлення шукається для налаштованих версії гри та лоадера. Кнопка «Оновити» виконує такі кроки:

1. Завантажує нову версію у тимчасовий `.part` файл.
2. Перевіряє контрольну суму (SHA-512 для Modrinth, SHA-1 для CurseForge).
3. Переносить старий файл у `.minecraft/.mc-manager-backup/<тип>/<дата-час>/`.
4. Кладе новий файл на його місце.

Є також «Оновити все». Закрийте гру перед оновленням.

Для CurseForge потрібен безкоштовний API-ключ (https://console.curseforge.com). Деякі автори забороняють завантаження через API, і тоді застосунок пробує публічний CDN. Якщо й це не спрацює, файл треба завантажити вручну.

### Дані зі світу (`level.dat`)

Показуються назва, версія гри та Data version, режим, складність, hardcore, команди, seed, спавн, ігровий час, погода, гравець, виміри, датапаки, ігрові правила, розмір на диску. Нижче є розділ «Усі дані level.dat», де завантажується повне NBT-дерево. Парсер толерантний до різних версій формату: якщо поля немає, воно просто не показується.

## Розробка без Docker

```bash
cd backend  && mvn spring-boot:run           # http://localhost:8080
cd frontend && npm install && npm start      # http://localhost:4200, /api проксюється на :8080
```

Змінні бекенду: `APP_MINECRAFT_PATH`, `APP_GAME_VERSION`, `APP_LOADER`, `APP_PROVIDER`, `CURSEFORGE_API_KEY`, `APP_DATA_DIR`. Вони задають лише початкові значення, потім налаштування живуть у `APP_DATA_DIR/settings.json` (`APP_DATA_DIR` за замовчуванням `./data`; налаштування змінюються на сторінці «Налаштування»).

## REST API

```
GET  /api/settings          PUT /api/settings          GET /api/settings/paths
GET  /api/content/{mods|resourcepacks|datapacks|shaderpacks}[?refresh=true]
GET  /api/content/{type}/updates[?refresh=true]
POST /api/content/{type}/install        {"id": "mods/foo.jar"}
POST /api/content/{type}/install-all
GET  /api/content/{type}/icon?id=...
GET  /api/worlds     GET /api/worlds/{folder}     GET /api/worlds/{folder}/raw     GET /api/worlds/{folder}/icon
```

## Структура

```
backend/    Spring Boot (settings, content, provider, world)
frontend/   Angular 21 + nginx (проксі /api -> backend)
docker-compose.yml
```

## Збірка десктопного застосунку

Потрібні JDK 21 і Node.js 22 або новіший. Інсталятор збирається лише для поточної ОС.

```bash
cd desktop
npm ci
npm run build:resources   # один jar з UI та бекендом, урізане середовище Java
npm start                 # запуск без пакування
npm run dist              # збірка інсталятора в desktop/dist
```

`build:resources` також залишає окремий jar у `backend/target/desktop/`.

Push гілки `release/**` збирає та перевіряє всі інсталятори в GitHub Actions. Push тегу `v*`, що збігається з версією в `desktop/package.json`, також публікує їх як реліз GitHub.
