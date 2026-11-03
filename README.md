# Minecraft Manager

See everything installed in your Minecraft, get names, icons and versions from Modrinth or CurseForge, update mods, resource packs, shaders and datapacks with one click, and start Minecraft Launcher.

You can use it as a [desktop app](#desktop-app) or as a [web app](#web-app) in your browser.

- **Backend:** Spring Boot 3.5, Java 21. Settings and caches are local JSON files, so there is no database.
- **Frontend:** Angular 21
- **Desktop:** Electron, with Java included

## Desktop app

### Install a release

Java, Docker and a database are not needed. Get the installer for your system from the [latest release](https://github.com/YValerii/minecraft-manager/releases/latest):

| System | File |
|---|---|
| Windows 10/11 | `minecraft-manager-<version>-win-x64.msi` or `minecraft-manager-<version>-win-x64.exe` |
| macOS, Apple Silicon | `minecraft-manager-<version>-mac-arm64.dmg` |
| macOS, Intel | `minecraft-manager-<version>-mac-x64.dmg` |
| Linux | `minecraft-manager-<version>-linux-x86_64.AppImage` or `minecraft-manager-<version>-linux-amd64.deb` |

The installers aren't code-signed yet, so the first launch needs one extra step:

- **Windows:** on the SmartScreen warning, choose "More info", then "Run anyway".
- **macOS:** drag the app to Applications, then run `xattr -cr "/Applications/Minecraft Manager.app"` in Terminal. Without this, macOS reports the app as damaged.
- **Linux AppImage:** run `chmod +x` on the file first. On Ubuntu 24.04 and later, also start it with `--no-sandbox`, or install the `.deb` instead.

Settings are stored in `%APPDATA%\Minecraft Manager` on Windows, `~/Library/Application Support/Minecraft Manager` on macOS and `~/.config/Minecraft Manager` on Linux.

### Build and run from source

You need:

- **JDK 21.** A JRE isn't enough, because the build uses `jlink`. Either put the JDK's `bin` folder on your `PATH` or set `JAVA_HOME` to the JDK folder.
- **Node.js 22.12 or newer.** An LTS version is recommended.

```bash
cd desktop
npm ci
npm run build:resources   # builds the UI, the backend and a trimmed Java runtime
npm start                 # runs the app without packaging it
```

Run `npm run build:resources` again after changing the frontend or backend.

To build an installer for the OS you're on (installers can't be cross-built), run this after `build:resources`:

```bash
npm run dist              # writes the installer to desktop/dist
```

## Web app

Pick whichever suits you. All three serve the same UI in your browser.

| Way to run | Needs | Address | Launcher button |
|---|---|---|---|
| [Release jar](#release-jar) | Java 21+ | http://localhost:8080 | Yes |
| [Docker](#docker) | Docker | http://localhost:4200 | No |
| [From source](#from-source) | JDK 21, Node.js 22.12+ | http://localhost:4200 | Yes |

### Release jar

Download `minecraft-manager-<version>.jar` from the [latest release](https://github.com/YValerii/minecraft-manager/releases/latest), then run:

```bash
java -jar minecraft-manager-<version>.jar
```

Your browser opens http://localhost:8080. Add `--server.port=9000` if that port is taken, and press Ctrl+C in the terminal to stop. Settings are saved in a `data` folder in the directory you run it from.

To build this jar yourself:

```bash
cd frontend
npm ci
npm run build
cd ../backend
./mvnw -Pbundle-ui clean package      # Windows: .\mvnw.cmd -Pbundle-ui clean package
```

The jar is written to `backend/target/desktop/`.

### Docker

```bash
docker compose up --build
```

Then open http://localhost:4200.

On Windows, `%APPDATA%\.minecraft` is mounted into the container by default. On other systems, or with a non-standard location, copy `.env.example` to `.env` and set the folder:

```env
MINECRAFT_DIR=C:/Users/Name/AppData/Roaming/.minecraft
# Linux:  MINECRAFT_DIR=/home/user/.minecraft
# macOS:  MINECRAFT_DIR=/Users/user/Library/Application Support/minecraft
```

The folder is mounted with write access, otherwise updates aren't possible.

The launcher button is disabled in Docker, because a container can't start programs on your computer.

### From source

For development, run the backend and frontend in two terminals. The frontend reloads on changes and proxies `/api` to the backend.

```bash
cd backend
./mvnw spring-boot:run                # Windows: .\mvnw.cmd spring-boot:run
```

```bash
cd frontend
npm install
npm start
```

Then open http://localhost:4200. The backend runs on http://localhost:8080.

## How to use it

### First-time setup

Open **Settings** and check these, then save:

- **Minecraft installation path.** Leave the default unless your game is somewhere else.
- **Minecraft version and mod loader.** Updates are only looked up for these. The defaults are 26.3 and neoforge.
- **Source.** Modrinth works without a key. CurseForge needs a free API key from https://console.curseforge.com.

### Browse what's installed

| Tab | What it shows |
|---|---|
| Mods | `mods/*.jar` |
| Resource packs | `resourcepacks/*.zip` and folders |
| Datapacks | `datapacks/` and `saves/<world>/datapacks/`, tagged with the world |
| Shaders | `shaderpacks/*.zip` and folders |
| Worlds | One tile per world. Click a tile for details from `level.dat`, including the full NBT tree. |

Use the search box and the filters to narrow a list down to items with updates or unrecognized items.

Files are identified by their hash, not their name, so a renamed file is still recognized. If a file isn't on Modrinth or CurseForge, such as a custom build or a folder pack, it's marked "Unrecognized" and shows the details stored inside the file instead.

### Update

Close the game first, then:

1. Open a tab and click **Check for updates**.
2. Click **Update** on one item, or **Update all**.

Each update is downloaded to a temporary file and its checksum is verified before anything is replaced. The old file is moved to `.minecraft/.mc-manager-backup/<type>/<date-time>/`, so you can restore it from there by hand.

Some CurseForge authors disallow downloads through the API. The app then tries the public CDN, and if that fails too, the file has to be downloaded manually.

### Start Minecraft

Click **▶ Launcher** in the top bar to open the official Minecraft Launcher. It's found automatically in these places:

| System | Locations |
|---|---|
| Windows | Microsoft Store / Xbox app install, `Program Files\Minecraft Launcher`, `Program Files (x86)\Minecraft Launcher` |
| macOS | `/Applications/Minecraft.app`, `~/Applications/Minecraft.app` |
| Linux | `minecraft-launcher` on your `PATH`, `/opt/minecraft-launcher`, Flatpak `com.mojang.Minecraft` |

If the launcher can't be found, the button is disabled. For a launcher in another location, set the `APP_LAUNCHER_PATH` environment variable to its full path and restart the app.

### Theme and language

Click the ☀️ / 🌙 button in the top bar to switch between light and dark. The app starts with your system's preference until you choose one. Pick a language from the list beside it. Both choices are remembered.

## Configuration

These environment variables configure the backend. `APP_MINECRAFT_PATH` to `CURSEFORGE_API_KEY` are only initial values: after the first run they live in `settings.json` in `APP_DATA_DIR` and are changed on the **Settings** page.

| Variable | Default | Purpose |
|---|---|---|
| `APP_MINECRAFT_PATH` | `%APPDATA%\.minecraft` or `~/.minecraft` | Minecraft folder |
| `APP_GAME_VERSION` | `26.3` | Game version for updates |
| `APP_LOADER` | `neoforge` | Mod loader for updates |
| `APP_PROVIDER` | `MODRINTH` | `MODRINTH` or `CURSEFORGE` |
| `CURSEFORGE_API_KEY` | empty | CurseForge API key |
| `APP_DATA_DIR` | `./data` | Where settings and caches are stored |
| `APP_LAUNCHER_PATH` | empty | Launcher location, read on every start. Empty means detect it automatically. |

## REST API

```
GET  /api/settings          PUT /api/settings          GET /api/settings/paths
GET  /api/content/{mods|resourcepacks|datapacks|shaderpacks}[?refresh=true]
GET  /api/content/{type}/updates[?refresh=true]
POST /api/content/{type}/install        {"id": "mods/foo.jar"}
POST /api/content/{type}/install-all
GET  /api/content/{type}/icon?id=...
GET  /api/worlds     GET /api/worlds/{folder}     GET /api/worlds/{folder}/raw     GET /api/worlds/{folder}/icon
GET  /api/launcher          POST /api/launcher/launch  {}
```

The API has no login, so it only listens on your own computer unless you change `SERVER_ADDRESS`.

## Project structure

```
backend/    Spring Boot (settings, content, provider, world, launcher)
frontend/   Angular 21 + nginx (proxies /api to the backend)
desktop/    Electron wrapper and installer build
docker-compose.yml
```

## Releases

Pushing a `release/**` branch builds and tests all installers in GitHub Actions. Pushing a `v*` tag that matches the version in `desktop/package.json` also publishes them as a GitHub release.
