import { chromium } from "@playwright/test"
const SP = "/private/tmp/claude-501/-Users-felixdietrich-Documents-Artemis2/8f7402fe-be82-4a0e-b2ca-8e9963e7dcad/scratchpad"
const b = await chromium.launch({ headless: true })
const p = await b.newPage({ deviceScaleFactor: 2, viewport: { width: 1500, height: 950 } })
p.on("console", (m) => { if (m.type() === "error" && !m.text().includes("ngsw")) console.log("CONSOLE", m.text().slice(0, 140)) })
await p.goto("http://localhost:9000/sign-in", { waitUntil: "domcontentloaded" })
await p.locator("#username").waitFor({ state: "visible", timeout: 30000 })
await p.locator("#username").fill("artemis_admin"); await p.locator("#username").blur()
await p.click("#continue-button")
await p.locator("#password").waitFor({ state: "visible" }); await p.locator("#password").fill("artemis_admin"); await p.locator("#password").blur()
await p.click("#login-button"); await p.waitForTimeout(4000)

const ROUTES = {
  managementDetail: "http://localhost:9000/course-management/9020/modeling-exercises/8",
  studentExampleSolution: "http://localhost:9000/courses/9020/exercises/8/example-solution",
}
for (const [name, url] of Object.entries(ROUTES)) {
  await p.goto(url, { waitUntil: "domcontentloaded" })
  await p.waitForTimeout(4000)
  const info = await p.evaluate(() => {
    const box = (s) => { const e = document.querySelector(s); if (!e) return null; const r = e.getBoundingClientRect(); return { w: Math.round(r.width), h: Math.round(r.height) } }
    const svg = document.querySelector(".readonly-diagram > svg")
    return {
      readonly: box(".readonly-diagram"),
      svg: svg ? { w: Math.round(svg.getBoundingClientRect().width), h: Math.round(svg.getBoundingClientRect().height), viewBox: svg.getAttribute("viewBox") } : null,
      liveCanvas: box(".apollon-editor"),
      hostChain: (() => { const out = []; let n = document.querySelector(".readonly-diagram"); for (; n && out.length < 5; n = n.parentElement) { const c = getComputedStyle(n); out.push(`${n.tagName.toLowerCase()}${typeof n.className === "string" && n.className.trim() ? "." + n.className.trim().split(/\s+/)[0] : ""} h=${Math.round(n.getBoundingClientRect().height)} ov=${c.overflow}`) } return out })(),
    }
  })
  console.log(name, JSON.stringify(info))
  const target = await p.locator(".readonly-diagram").first().isVisible().catch(() => false)
  if (target) await p.locator(".readonly-diagram").first().screenshot({ path: `${SP}/exsol-${name}.png` })
}
await b.close()
