#!/usr/bin/env node
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const exec = promisify(execFile);
const adb = process.env.ADB || 'adb';
const serial = process.env.ANDROID_SERIAL;
const port = Number(process.env.PORT || 8765);

async function command(file, args) {
  return (await exec(file, args, { timeout: 15000, maxBuffer: 8 * 1024 * 1024 })).stdout;
}

async function status() {
  const result = { checkedAt: new Date().toISOString(), device: '', error: '', lines: [], gradle: 'Unknown' };
  try {
    const devices = (await command(adb, ['devices', '-l'])).split('\n').slice(1)
      .map(line => line.trim()).filter(Boolean).map(line => line.split(/\s+/));
    const selected = serial ? devices.find(device => device[0] === serial) : devices.length === 1 ? devices[0] : null;
    if (!selected) {
      result.error = devices.length > 1 && !serial ? 'Multiple devices found. Set ANDROID_SERIAL to choose one.' : 'No matching Android device found.';
    } else if (selected[1] !== 'device') {
      result.error = `Device ${selected[0]} is ${selected[1]}; authorize or reconnect it.`;
    } else {
      result.device = selected.join(' ');
      const output = await command(adb, ['-s', selected[0], 'logcat', '-d', '-v', 'threadtime', '-s', 'DishCorpus:I', 'TestRunner:I', '*:S']);
      const lines = output.split('\n').filter(line => /\b(DishCorpus|TestRunner)\s*:/.test(line));
      const starts = lines.map((line, index) => /TestRunner\s*:\s*started: dumpCorpus\(/.test(line) ? index : -1).filter(index => index >= 0);
      result.lines = lines.slice(starts.at(-1) ?? 0).slice(-500);
    }
  } catch (error) {
    result.error = error.code === 'ENOENT' ? `Cannot find ${adb}. Install adb or set ADB to its path.` : `ADB: ${error.message}`;
  }
  try {
    const processes = await command('ps', ['-axo', 'command']);
    const running = processes.split('\n').some(line => /org\.gradle\.wrapper\.GradleWrapperMain/.test(line) && line.includes(':app:connectedDebugAndroidTest'));
    result.gradle = running ? 'A connected-test Gradle client is running (not necessarily this run)' : 'No connected-test Gradle client found; final result unknown';
  } catch {
    result.gradle = 'Could not inspect Gradle processes; final result unknown';
  }
  return result;
}

const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Android test monitor</title>
<style>
:root{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#202a2a;background:#f3f5f2}*{box-sizing:border-box}body{margin:0}header{background:#1b302d;color:#fff;padding:20px 24px}header h1{font:600 20px Georgia,serif;margin:0}header p{color:#b9d2c5;margin:7px 0 0;font-size:12px}main{max-width:1100px;margin:auto;padding:24px}.overview{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:#d8dfd9;border:1px solid #d8dfd9}.stat{background:#fff;padding:16px;min-width:0}.label{font-size:11px;text-transform:uppercase;color:#61716b}.value{font-size:18px;margin-top:8px;overflow-wrap:anywhere}.status{margin:18px 0;padding:12px 14px;background:#e4eee8;border-left:3px solid #43866a;font-size:13px;line-height:1.5}.status.error{background:#fff0e9;border-color:#c75a37}.details{font-size:12px;line-height:1.7;color:#52615b;overflow-wrap:anywhere}.loghead{display:flex;justify-content:space-between;align-items:center;margin-top:24px}.loghead h2{font:600 16px Georgia,serif}.loghead button{border:1px solid #a8b9ae;background:white;color:#263c32;padding:7px 10px;cursor:pointer;font:inherit;font-size:12px}pre{height:55vh;min-height:260px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;background:#202b29;color:#e4eee8;padding:16px;font-size:12px;line-height:1.6;margin:0}@media(max-width:650px){.overview{grid-template-columns:repeat(2,minmax(0,1fr))}main{padding:16px}}
</style></head><body><header><h1>Android test monitor</h1><p>Device activity &middot; read-only</p></header><main>
<div class="overview"><div class="stat"><div class="label">Device run</div><div class="value" id="run">Waiting</div></div><div class="stat"><div class="label">Posts processed</div><div class="value" id="posts">&mdash;</div></div><div class="stat"><div class="label">Images</div><div class="value" id="images">&mdash;</div></div><div class="stat"><div class="label">Warnings</div><div class="value" id="warnings">&mdash;</div></div></div>
<div class="status" id="message">Connecting to device...</div><div class="details" id="device"></div><div class="details" id="gradle"></div><div class="details" id="updated"></div>
<div class="loghead"><h2>Test activity</h2><button id="follow" type="button">Following latest</button></div><pre id="log">Waiting for logs...</pre></main>
<script>
const element = id => document.getElementById(id); let follow = true;
element('follow').onclick = () => { follow = !follow; element('follow').textContent = follow ? 'Following latest' : 'Follow latest'; };
async function refresh() { try {
 const response = await fetch('/status', {cache:'no-store'}); const data = await response.json(); const lines = data.lines;
 const started = lines.some(line => /started: dumpCorpus\\(/.test(line)); const finished = lines.findLast(line => /finished: dumpCorpus\\(/.test(line)); const failed = lines.some(line => /failed: dumpCorpus\\(/.test(line)); const summary = lines.findLast(line => /DishCorpus\\s*:\\s*DONE /.test(line));
 const posts = lines.filter(line => /DishCorpus\\s*:\\s*post .*: \\d+ images \\(total \\d+\\)/.test(line)); const lastPost = posts.at(-1); const imageCount = lastPost && lastPost.match(/\\(total (\\d+)\\)/); const summaryImages = summary && summary.match(/images=(\\d+)/); const warnings = lines.filter(line => /\\sW\\s+\\d+\\s+\\d+\\s+DishCorpus\\s*:/.test(line));
 element('run').textContent = failed ? 'Failed on device' : finished ? 'Finished on device' : started ? 'Started; check activity' : lines.length ? 'Earlier logs' : 'No test logs'; element('posts').textContent = started ? posts.length : 'Unknown'; element('images').textContent = summaryImages?.[1] || imageCount?.[1] || 'Unknown'; element('warnings').textContent = started ? warnings.length : 'Unknown';
 const message = element('message'); message.className = 'status' + (data.error || failed ? ' error' : ''); message.textContent = data.error || (summary ? summary.replace(/^.*?DishCorpus\\s*:\\s*/, '') : started ? 'Progress is based on the latest test start visible in logcat. A started test may have stopped without a completion log.' : 'No corpus test start is visible in the current device log buffer; displayed activity may be incomplete.');
 element('device').textContent = 'Device: ' + (data.device || 'unavailable'); element('gradle').textContent = 'Gradle: ' + data.gradle; element('updated').textContent = 'Updated: ' + new Date(data.checkedAt).toLocaleTimeString();
 const log = element('log'); const next = lines.join('\\n') || 'No matching log entries.'; if (log.textContent !== next) { log.textContent = next; if (follow) log.scrollTop = log.scrollHeight; }
 } catch (error) { element('message').className = 'status error'; element('message').textContent = 'Monitor connection error: ' + error.message; } }
refresh(); setInterval(refresh, 5000);
</script></body></html>`;

http.createServer(async (request, response) => {
  if (request.url === '/status') {
    response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify(await status()));
  } else if (request.url === '/') {
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    response.end(html);
  } else {
    response.writeHead(404); response.end('Not found');
  }
}).listen(port, '127.0.0.1', () => console.log(`Android test monitor: http://127.0.0.1:${port}`));