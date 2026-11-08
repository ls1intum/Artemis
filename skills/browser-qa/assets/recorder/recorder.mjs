// Video-recording harness for Artemis browser QA (artemis-browser-qa skill).
//
// Playwright chromium + native video recording, with a visible cursor overlay
// and click ripples so recordings read like a human click-through.
// Actions are real trusted input events (mouse.move/down/up, keyboard.type).
// The overlay is presentation only; it never drives the app.
//
// Usage: copy this file into a work dir OUTSIDE the repo under review that has
// `playwright-core` (and ideally `ffmpeg-static`) installed. See README.md.
import { chromium } from 'playwright-core';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const HOME = process.env.HOME || os.homedir();

// mp4 conversion: prefer ffmpeg-static, fall back to a system ffmpeg.
let FFMPEG = null;
try { FFMPEG = require('ffmpeg-static'); } catch { FFMPEG = 'ffmpeg'; }

// Newest Chromium in the Playwright cache ($PLAYWRIGHT_BROWSERS_PATH, else the
// platform default), or undefined.
function cachedChromium() {
  const platform = process.platform;
  const defaultCache = {
    darwin: `${HOME}/Library/Caches/ms-playwright`,
    win32: path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'ms-playwright'),
  }[platform] ?? `${HOME}/.cache/ms-playwright`;
  const cache = process.env.PLAYWRIGHT_BROWSERS_PATH || defaultCache;
  const subs = {
    darwin: ['chrome-mac-arm64', 'chrome-mac-x64', 'chrome-mac'].flatMap(dir => [
      `${dir}/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing`,
      `${dir}/Chromium.app/Contents/MacOS/Chromium`,
    ]),
    win32: ['chrome-win64/chrome.exe', 'chrome-win/chrome.exe'],
  }[platform] ?? ['chrome-linux64/chrome', 'chrome-linux/chrome'];
  const revs = fs.existsSync(cache)
    ? fs.readdirSync(cache).filter(d => /^chromium-\d+$/.test(d)).sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]))
    : [];
  for (const rev of revs) {
    for (const sub of subs) {
      const p = path.join(cache, rev, sub);
      if (fs.existsSync(p)) return p;
    }
  }
  return undefined;
}

// Chromium: CHROME_PATH if set; else whatever this playwright-core launches by
// itself (its own revision, or its headless shell when headless); else, when
// Playwright reports that browser missing, the newest Chromium in the cache.
async function launchChromium(headless) {
  if (process.env.CHROME_PATH) return chromium.launch({ executablePath: process.env.CHROME_PATH, headless });
  try {
    return await chromium.launch({ headless });
  } catch (error) {
    if (!/Executable doesn't exist/i.test(String(error))) throw error;
    const fallback = cachedChromium();
    if (!fallback) throw new Error('No Chromium found. Run `npx playwright-core install chromium` or set CHROME_PATH.', { cause: error });
    return chromium.launch({ executablePath: fallback, headless });
  }
}

const CURSOR_SCRIPT = `
(() => {
  if (window.__qaCursorInstalled) return;
  window.__qaCursorInstalled = true;
  const install = () => {
    // Init scripts can run before documentElement exists on redirect chains;
    // retry instead of throwing (an uncaught error here pollutes the page's
    // error log and shows up as a fake "app defect" in QA evidence).
    if (!document.documentElement) { setTimeout(install, 30); return; }
    if (document.getElementById('__qa_cursor')) return;
    const style = document.createElement('style');
    style.textContent = \`
      #__qa_cursor { position: fixed; z-index: 2147483647; width: 22px; height: 22px;
        pointer-events: none; left: 0; top: 0; transform: translate(-4px, -3px);
        transition: none; }
      #__qa_cursor svg { display: block; filter: drop-shadow(0 1px 2px rgba(0,0,0,.55)); }
      .__qa_ripple { position: fixed; z-index: 2147483646; width: 36px; height: 36px;
        border-radius: 50%; border: 3px solid #ffb300; pointer-events: none;
        transform: translate(-50%,-50%) scale(.3); opacity: .95;
        animation: __qa_rip .45s ease-out forwards; }
      @keyframes __qa_rip { to { transform: translate(-50%,-50%) scale(1.6); opacity: 0; } }
    \`;
    (document.head || document.documentElement).appendChild(style);
    const c = document.createElement('div');
    c.id = '__qa_cursor';
    c.innerHTML = '<svg viewBox="0 0 24 24"><path d="M5 2 L5 19 L9.5 15.5 L12.5 22 L15 21 L12 14.5 L18 14 Z" fill="#fff" stroke="#222" stroke-width="1.4" stroke-linejoin="round"/></svg>';
    document.documentElement.appendChild(c);
    document.addEventListener('mousemove', e => {
      c.style.left = e.clientX + 'px'; c.style.top = e.clientY + 'px';
    }, true);
    document.addEventListener('mousedown', e => {
      const r = document.createElement('div');
      r.className = '__qa_ripple';
      r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px';
      document.documentElement.appendChild(r);
      setTimeout(() => r.remove(), 500);
    }, true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
  // SPA route changes keep the same document, but reloads need re-install.
  install();
})();
`;

/**
 * Sign in off camera and return a storageState object, so recorded clips start
 * already authenticated at the flow's start screen (login churn stays out of clips).
 * Handles both the newer '/sign-in' route and older branches where the form is at '/'.
 * Also suppresses the "Stay Secure with Passkeys" nag modal (localStorage-gated)
 * so it never interrupts a recorded take.
 */
export async function prepareAuth(username, password, baseURL = 'http://localhost:9000') {
  const browser = await launchChromium(true);
  try {
    return await signIn(browser, username, password, baseURL);
  } finally {
    await browser.close();
  }
}

async function signIn(browser, username, password, baseURL) {
  const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  await page.goto('/sign-in', { waitUntil: 'networkidle' });
  // The sign-in form may be two-step (username -> Continue -> password) or single-step; handle both.
  const usernameField = page.locator('#username, input[name="username"], input').first();
  await usernameField.waitFor({ state: 'visible', timeout: 60000 });
  await usernameField.fill(username);
  const continueButton = page.locator('button:has-text("Continue"), button:has-text("Weiter")');
  if (await continueButton.count()) { await continueButton.first().click(); }
  await page.locator('#password').waitFor({ state: 'visible', timeout: 20000 });
  await page.fill('#password', password);
  // Judge sign-in by the auth calls, not by the landing route, which differs between branches.
  const authResponse = page.waitForResponse(r => r.url().includes('/api/core/public/authenticate'), { timeout: 30000 });
  await page.locator('button[type="submit"]').last().click();
  const auth = await authResponse;
  if (!auth.ok()) throw new Error(`Sign-in as ${username} failed: HTTP ${auth.status()}`);
  const account = await context.request.get('/api/core/public/account');
  const login = account.ok() ? (await account.json()).login : undefined;
  if (login !== username) throw new Error(`Sign-in as ${username} did not hold: account endpoint returned ${account.status()} for ${login ?? 'nobody'}`);
  await page.waitForURL(url => !url.pathname.startsWith('/sign-in'), { timeout: 30000 });
  await page.evaluate(() => {
    const future = new Date();
    future.setDate(future.getDate() + 30);
    localStorage.setItem('earliestSetupPasskeyReminderDate', JSON.stringify(future.toISOString()));
  });
  return context.storageState();
}

export async function startSession({ name, outDir, baseURL = 'http://localhost:9000', viewport = { width: 1440, height: 810 }, headless = true, storageState = undefined }) {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await launchChromium(headless);
  let context;
  try {
    context = await browser.newContext({
      baseURL,
      viewport,
      recordVideo: { dir: outDir, size: viewport },
      ignoreHTTPSErrors: true,
      storageState,
    });
    await context.addInitScript(CURSOR_SCRIPT);
  } catch (error) {
    await browser.close();
    throw error;
  }
  const events = [];
  const pending = new Set();
  let capture = null;
  let requestNumber = 0;
  const page = await context.newPage();
  page.on('console', m => {
    if (m.type() === 'error' || m.type() === 'warning') {
      events.push({ kind: 'console-' + m.type(), text: m.text().slice(0, 600), at: page.url() });
    }
  });
  page.on('pageerror', e => events.push({ kind: 'pageerror', text: String(e).slice(0, 600), stack: e.stack, at: page.url() }));
  page.on('response', response => {
    const request = response.request();
    if (!['fetch', 'xhr', 'document'].includes(request.resourceType())) return;
    const url = new URL(response.url());
    if (/\/api\/core\/public\/(authenticate|account)(?:\/|$)/.test(url.pathname)) return;
    const event = { kind: 'http', status: response.status(), method: request.method(), url: response.url(), resourceType: request.resourceType() };
    events.push(event);
    if (!capture || !capture.match(request)) return;
    const caseId = capture.caseId;
    const index = ++requestNumber;
    event.case = caseId;
    const task = (async () => {
      const dir = path.join(outDir, 'network');
      fs.mkdirSync(dir, { recursive: true });
      const prefix = path.join(dir, `${caseId}-${index}`);
      const requestBody = request.postDataBuffer();
      if (requestBody !== null) {
        fs.writeFileSync(`${prefix}.request`, requestBody);
        event.requestBody = `${prefix}.request`;
      }
      const responseBody = await response.body();
      fs.writeFileSync(`${prefix}.response`, responseBody);
      event.responseBody = `${prefix}.response`;
      event.contentType = response.headers()['content-type'] ?? null;
    })().catch(error => { event.captureError = String(error); });
    pending.add(task);
    task.finally(() => pending.delete(task));
  });

  const session = {
    browser, context, page, events, name, outDir,
    /** Note an expected event (e.g. an intentional 403 check) so the report can separate it. */
    note(text) { events.push({ kind: 'note', text }); },
    captureNetwork(caseId, match) {
      if (!/^[A-Za-z0-9_-]+$/.test(caseId) || typeof match !== 'function') throw new Error('Invalid network capture');
      capture = { caseId, match };
    },
    stopNetworkCapture() { capture = null; },
    async finish() {
      await Promise.all([...pending]);
      const video = page.video();
      await context.close();
      const webm = await video.path();
      const finalWebm = path.join(outDir, `${name}.webm`);
      fs.renameSync(webm, finalWebm);
      let mp4 = path.join(outDir, `${name}.mp4`);
      try {
        execFileSync(FFMPEG, ['-y', '-i', finalWebm, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '21', '-preset', 'veryfast', '-movflags', '+faststart', mp4], { stdio: 'pipe' });
      } catch (e) {
        mp4 = null; // no ffmpeg available: deliver the webm and say so in the report
        events.push({ kind: 'note', text: 'mp4 conversion unavailable: ' + String(e).slice(0, 200) });
      }
      fs.writeFileSync(path.join(outDir, `${name}.events.json`), JSON.stringify(events, null, 2));
      await browser.close();
      return { mp4, webm: finalWebm, events };
    },
  };
  return session;
}

/** Move the cursor to the element like a human, then really click it. */
export async function humanClick(page, target, opts = {}) {
  const loc = typeof target === 'string' ? page.locator(target).first() : target.first ? target.first() : target;
  await loc.waitFor({ state: 'visible', timeout: opts.timeout ?? 20000 });
  await loc.scrollIntoViewIfNeeded();
  await page.waitForTimeout(120);
  const box = await loc.boundingBox();
  if (!box) throw new Error('no bounding box for target');
  const x = box.x + box.width / 2, y = box.y + box.height / 2;
  await page.mouse.move(x, y, { steps: 28 });
  await page.waitForTimeout(180);
  await page.mouse.down();
  await page.waitForTimeout(70);
  await page.mouse.up();
  await page.waitForTimeout(opts.settle ?? 500);
}

/** Click into a field, clear it with real keystrokes, type like a human. */
export async function humanType(page, target, text, opts = {}) {
  await humanClick(page, target, { ...opts, settle: 150 });
  if (opts.clear !== false) {
    await page.keyboard.press('ControlOrMeta+a');
    await page.keyboard.press('Delete');
    await page.waitForTimeout(120);
  }
  await page.keyboard.type(String(text), { delay: opts.delay ?? 50 });
  await page.waitForTimeout(250);
}

export async function pause(page, ms = 900) { await page.waitForTimeout(ms); }

/** Verified Artemis toast selector. Query after every navigation or action. */
export async function probeToasts(page, label, events) {
  const toasts = await page.evaluate(() =>
    [...document.querySelectorAll('jhi-alert-overlay .alert-inner')].map(e => ({
      cls: e.className,
      msg: e.querySelector('.message')?.textContent?.trim(),
    }))
  ).catch(() => []);
  for (const t of toasts) events.push({ kind: 'toast', label, ...t });
  return toasts;
}

/** Sign in through the real login form (only when the login itself is the flow on camera). */
export async function login(page, username, password) {
  await page.goto('/sign-in', { waitUntil: 'networkidle' });
  await page.locator('#username').waitFor({ state: 'visible', timeout: 30000 });
  await humanType(page, '#username', username);
  await humanType(page, '#password', password);
  await humanClick(page, 'button[type="submit"]');
  await page.waitForURL(/courses|course-management/, { timeout: 30000 });
  await page.waitForTimeout(1000);
}
