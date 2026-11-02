'use strict';
// Launches the packaged app from dist/ in smoke-test mode. Exit code 0 means the window opened, the
// bundled Java runtime started the backend, and the UI reached it through the app:// protocol.

const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const pkg = require('../package.json');

const dist = path.resolve(__dirname, '..', 'dist');
const dirs = fs.readdirSync(dist);
const find = (prefix) => {
  const dir = dirs.find((d) => d.startsWith(prefix) && fs.statSync(path.join(dist, d)).isDirectory());
  if (!dir) {
    throw new Error(`No ${prefix}* directory in ${dist}: ${dirs.join(', ')}`);
  }
  return path.join(dist, dir);
};

let command;
let args = [];
if (process.platform === 'win32') {
  command = path.join(find('win'), `${pkg.productName}.exe`);
} else if (process.platform === 'darwin') {
  command = path.join(find('mac'), `${pkg.productName}.app`, 'Contents', 'MacOS', pkg.productName);
} else {
  command = 'xvfb-run';
  // The setuid sandbox helper isn't set up in an unpacked build.
  args = ['-a', path.join(find('linux'), pkg.name), '--no-sandbox'];
}

const res = spawnSync(command, args, {
  stdio: 'inherit',
  timeout: 120_000,
  env: { ...process.env, MCM_SMOKE_TEST: '1', ELECTRON_ENABLE_LOGGING: '1' },
});
if (res.status !== 0) {
  console.error(`Smoke test failed: ${res.error?.message ?? `exit code ${res.status}`}`);
  process.exit(1);
}
console.log('Smoke test passed');
