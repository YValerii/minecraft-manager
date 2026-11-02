# Minecraft Manager

*Read in another language: [Українська](README.uk.md)*

A web app that shows everything installed in your Minecraft, pulls names, icons, and versions from Modrinth (or CurseForge), and updates mods, resource packs, shaders, and datapacks with one click.

- **Backend:** Spring Boot 3.5, Java 21, PostgreSQL
- **Frontend:** Angular 21 (standalone, signals, zoneless)
- **Run:** Docker Compose, one command

## Running

```bash
docker compose up --build
```

Open http://localhost:4200.

By default, `%APPDATA%\.minecraft` (Windows) is mounted into the container. On another OS or with a non-standard location, set the folder in `.env` (template in `.env.example`):

```env
MINECRAFT_DIR=C:/Users/Name/AppData/Roaming/.minecraft
# Linux:  MINECRAFT_DIR=/home/user/.minecraft
# macOS:  MINECRAFT_DIR=/Users/user/Library/Application Support/minecraft
```

The folder is mounted with write access, otherwise updates are not possible.

## Features

| Tab | What it shows |
|---|---|
| Mods | `mods/*.jar` |
| Resource packs | `resourcepacks/*.zip` and folders |
| Datapacks | `datapacks/` and `saves/<world>/datapacks/` (tagged with the world) |
| Shaders | `shaderpacks/*.zip` and folders |
| Worlds | tiles with icon, name, game version; clicking opens a modal with data from `level.dat` |
| Settings | Minecraft path, folders, game version, loader, source (Modrinth / CurseForge), CurseForge key |

Defaults: Minecraft **26.3**, loader **neoforge**, source **Modrinth**. Everything can be changed on the "Settings" page.

### How files are recognized

A file is identified by its hash, not by its name. Modrinth uses SHA-1, CurseForge uses the murmur2 fingerprint. So the file name can be anything. The correct name, description, icon, and current version are pulled from the site. The result is cached in Postgres.

If the file isn't found on the site (a custom build, a folder pack), data read from the file itself is shown instead: `neoforge.mods.toml`, `mods.toml`, `fabric.mod.json`, `quilt.mod.json`, `mcmod.info`, `pack.mcmeta`, and `pack.png`. Such an item is marked as "Unrecognized".

### Updating

Updates are looked up for the configured game version and loader. The "Update" button performs these steps:

1. Downloads the new version into a temporary `.part` file.
2. Verifies the checksum (SHA-512 for Modrinth, SHA-1 for CurseForge).
3. Moves the old file to `.minecraft/.mc-manager-backup/<type>/<date-time>/`.
4. Puts the new file in its place.

There's also an "Update all" option. Close the game before updating.

CurseForge requires a free API key (https://console.curseforge.com). Some authors disallow downloads via the API, in which case the app falls back to the public CDN. If that doesn't work either, the file has to be downloaded manually.

### World data (`level.dat`)

Shows the name, game version and data version, game mode, difficulty, hardcore, commands, seed, spawn, play time, weather, player, dimensions, datapacks, game rules, and disk size. Below that is an "All level.dat data" section where the full NBT tree is loaded. The parser is tolerant of different format versions: if a field is missing, it's simply not shown.

## Development without Docker

```bash
# Postgres, for example:
docker run -d --name mcdb -e POSTGRES_DB=mcmanager -e POSTGRES_USER=mcmanager \
  -e POSTGRES_PASSWORD=mcmanager -p 5432:5432 postgres:17-alpine

cd backend  && mvn spring-boot:run           # http://localhost:8080
cd frontend && npm install && npm start      # http://localhost:4200, /api proxied to :8080
```

Backend variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_MINECRAFT_PATH`, `APP_GAME_VERSION`, `APP_LOADER`, `APP_PROVIDER`, `CURSEFORGE_API_KEY`. They only set the initial values — after that, settings live in the database.

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

## Structure

```
backend/    Spring Boot (settings, content, provider, world)
frontend/   Angular 21 + nginx (proxies /api -> backend)
docker-compose.yml
```
