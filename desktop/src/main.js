'use strict';

const { app, BrowserWindow, Menu, dialog, nativeTheme, net, shell } = require('electron');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const netSocket = require('node:net');
const path = require('node:path');

const BACKEND_START_TIMEOUT_MS = 60_000;
// localStorage is per origin, so the UI's saved theme and language only survive a restart if the port stays the same.
const PREFERRED_PORT = 38517;
// CI launches the packaged app with this set: it checks the UI can reach the backend, then exits.
const SMOKE_TEST = process.env.MCM_SMOKE_TEST === '1';

const resources = app.isPackaged ? process.resourcesPath : path.join(__dirname, '..', 'build-output');

let backend = null;
let backendPort = null;
let quitting = false;
let mainWindow = null;

function backendOrigin() {
  return `http://127.0.0.1:${backendPort}`;
}

function listenAndRelease(port) {
  return new Promise((resolve, reject) => {
    const server = netSocket.createServer();
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => {
      const { port: bound } = server.address();
      server.close(() => resolve(bound));
    });
  });
}

/** PREFERRED_PORT if it's free, otherwise whichever port the OS picks. */
function choosePort() {
  return listenAndRelease(PREFERRED_PORT).catch(() => listenAndRelease(0));
}

function fail(message) {
  if (SMOKE_TEST) {
    console.error(`SMOKE FAIL: ${message}`);
    app.exit(1);
    return;
  }
  dialog.showErrorBox('Minecraft Manager', message);
  stopBackend();
  app.quit();
}

function startBackend(port) {
  const logDir = app.getPath('logs');
  fs.mkdirSync(logDir, { recursive: true });
  const logFile = path.join(logDir, 'backend.log');
  const log = fs.openSync(logFile, 'w');
  const java = path.join(resources, 'runtime', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');

  backend = spawn(
    java,
    [
      '-XX:TieredStopAtLevel=1',
      '-XX:+UseSerialGC',
      '-Djava.awt.headless=true',
      '-jar',
      path.join(resources, 'backend', 'app.jar'),
      '--server.address=127.0.0.1',
      `--server.port=${port}`,
      '--app.exit-on-stdin-close=true',
      '--app.open-browser=false',
    ],
    {
      env: { ...process.env, APP_DATA_DIR: app.getPath('userData') },
      // The backend exits when this pipe closes, so it can't outlive a crashed or killed app.
      stdio: ['pipe', log, log],
      windowsHide: true,
    },
  );
  backend.once('error', (err) => fail(`Could not start the backend: ${err.message}`));
  backend.once('exit', (code) => {
    backend = null;
    if (!quitting) {
      fail(`The backend stopped unexpectedly (exit code ${code}).\n\nLog: ${logFile}`);
    }
  });
  return logFile;
}

async function waitForBackend(port, logFile) {
  const deadline = Date.now() + BACKEND_START_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (!backend) {
      return false; // Already reported by the exit handler.
    }
    try {
      const res = await net.fetch(`http://127.0.0.1:${port}/api/settings`);
      if (res.ok) {
        return true;
      }
    } catch {
      // Not listening yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  fail(`The backend did not start in time.\n\nLog: ${logFile}`);
  return false;
}

function stopBackend() {
  quitting = true;
  if (backend) {
    backend.kill();
    backend = null;
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 480,
    minHeight: 400,
    show: false,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#12151a' : '#f4f6f8',
    title: 'Minecraft Manager',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Links to Modrinth, CurseForge etc. belong in the user's browser, not in the app window.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (new URL(url).origin !== backendOrigin()) {
      event.preventDefault();
    }
  });
}

async function runSmokeTest() {
  const result = await mainWindow.webContents.executeJavaScript(`
    (async () => {
      const get = await fetch('/api/settings');
      const settings = await get.json();
      const put = await fetch('/api/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings),
      });
      const route = await fetch('/settings');
      return {
        get: get.status,
        put: put.status,
        spaRoute: route.status,
        ui: !!document.querySelector('app-root a.tab'),
      };
    })()
  `);
  console.log(`SMOKE RESULT: ${JSON.stringify(result)}`);
  stopBackend();
  const ok = result.get === 200 && result.put === 200 && result.spaRoute === 200 && result.ui;
  app.exit(ok ? 0 : 1);
}

async function launch() {
  if (process.platform !== 'darwin') {
    Menu.setApplicationMenu(null); // macOS keeps its default menu, which provides copy and paste.
  }
  createWindow();
  await mainWindow.loadFile(path.join(__dirname, 'loading.html'));

  backendPort = await choosePort();
  const logFile = startBackend(backendPort);
  if (!(await waitForBackend(backendPort, logFile)) || !mainWindow) {
    return;
  }
  await mainWindow.loadURL(`${backendOrigin()}/`);
  if (SMOKE_TEST) {
    await runSmokeTest();
  }
}

// A second copy would run a second backend over the same settings files.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
    }
  });
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', stopBackend);
  app.whenReady().then(launch).catch((err) => fail(String(err?.stack ?? err)));
}
