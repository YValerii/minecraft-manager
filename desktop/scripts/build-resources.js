'use strict';
// Builds what the installer ships besides Electron: one jar with the UI and backend inside, and a
// trimmed Java runtime for the current OS/CPU. Output: desktop/build-output/.

const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const isWindows = process.platform === 'win32';
const repo = path.resolve(__dirname, '..', '..');
const out = path.resolve(__dirname, '..', 'build-output');

// jlink can't infer these from a Spring Boot fat jar. java.desktop is needed for java.beans (used
// by Spring), jdk.crypto.ec for TLS to Modrinth/CurseForge, jdk.zipfs for reading mod archives.
const MODULES = [
  'java.base', 'java.desktop', 'java.instrument', 'java.logging', 'java.management', 'java.naming',
  'java.net.http', 'java.scripting', 'java.security.jgss', 'java.sql', 'java.xml', 'jdk.charsets',
  'jdk.crypto.ec', 'jdk.management', 'jdk.unsupported', 'jdk.zipfs',
];

function run(command, args, cwd, shell = false) {
  console.log(`\n> ${command} ${args.join(' ')}`);
  const res = spawnSync(command, args, { cwd, stdio: 'inherit', shell });
  if (res.status !== 0) {
    throw new Error(`${command} failed (${res.error?.message ?? `exit code ${res.status}`})`);
  }
}

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'backend'), { recursive: true });

const frontend = path.join(repo, 'frontend');
// Not npm ci unconditionally: it deletes node_modules, which would break a running ng serve.
if (!fs.existsSync(path.join(frontend, 'node_modules'))) {
  run('npm', ['ci'], frontend, true);
}
run('npx', ['ng', 'build'], frontend, true);

const backend = path.join(repo, 'backend');
const mvnArgs = ['-q', '-B', '-DskipTests', '-Pbundle-ui', 'clean', 'package'];
if (isWindows) {
  run('.\\mvnw.cmd', mvnArgs, backend, true);
} else {
  run('./mvnw', mvnArgs, backend);
}
const jar = fs.readdirSync(path.join(backend, 'target', 'desktop')).find((f) => /^minecraft-manager-.*\.jar$/.test(f));
fs.copyFileSync(path.join(backend, 'target', 'desktop', jar), path.join(out, 'backend', 'app.jar'));

const jlink = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', isWindows ? 'jlink.exe' : 'jlink')
  : 'jlink';
run(jlink, [
  '--add-modules', MODULES.join(','),
  '--strip-java-debug-attributes',
  '--no-header-files',
  '--no-man-pages',
  '--compress=zip-6',
  '--output', path.join(out, 'runtime'),
], repo);

console.log(`\nResources ready in ${out}`);
