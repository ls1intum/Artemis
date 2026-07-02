# Plan: Migrate the Artemis PDF stack to EmbedPDF

## Context

Artemis ships **three** PDF libraries and **two** pdf.js engines:
- Lecture **viewer** (`src/main/webapp/app/lecture/shared/pdf-viewer/`) uses `ngx-extended-pdf-viewer@27` (vendors pdf.js 5.6.1113, copies a 21 MB asset tree).
- Instructor **preview/editor** (`src/main/webapp/app/lecture/manage/pdf-preview/`) uses `pdfjs-dist@6.0.227` (canvas thumbnails) + `pdf-lib@1.17.1` (page mutation).

This is a dual pdf.js engine skew, three dependencies, and a stringly-typed pdf.js `eventBus` integration. **EmbedPDF** (`@embedpdf/*`, MIT, PDFium compiled to WebAssembly, no pdf.js) replaces **all three** with one modern TypeScript engine. The goal is one PDF dependency family, a single engine, and the PDFium WASM self-hosted from the Artemis server (gzipped, background-prefetched after first render, durably cached).

**Capability verified** against the published `@embedpdf` v2.14.4 source (`packages/models/src/pdf.ts`, `packages/engines/src/lib/pdfium/engine.ts`): the engine is standalone (`createPdfiumEngine(wasmUrl)` returns a `PdfEngine`), so it renders arbitrary pages of multiple open documents with no mounted viewer, and the full edit pipeline (`createDocument` / `importPages(insertIndex)` / `deletePage` / `extractPages` / `saveAsCopy → ArrayBuffer`) and viewer plugins (render, selection, search, zoom, scroll, fullscreen, export, i18n) all exist. Known caveats are folded into the sections below.

**Locked decisions (confirmed with the user):**
1. **Drop the iframe.** EmbedPDF is headless with no global pdf.js singleton, so render directly in `PdfViewerComponent` and remove the `pdf-viewer-iframe` route + postMessage protocol.
2. **One combined PR** migrating both clusters and removing all three old libraries.
3. **Spike first (Phase 0)** before the full build, to settle the two real unknowns: the zoneless Angular bridge over EmbedPDF's headless store, and PDFium `saveAsCopy` fidelity on real Artemis lecture decks.

## Key facts grounding this plan

- Build: `@angular/build:application` (esbuild) outputs to `build/resources/main/static/`, served by Spring Boot. Static assets are copied via the `angular.json` `assets` array (monaco `vs/` and the pdfjs worker are the precedents). The builder bundles module workers (`new Worker(new URL(..., import.meta.url), {type:'module'})`).
- Serving/cache: `StaticResourcesConfiguration.java` gives `/content|assets|vs/**` a public cache; `CachingHttpHeadersFilter.java` (`WebConfigurer.java:89`) gives `*.js/*.css/i18n` a long immutable cache.
- Compression: `server.compression` (`application-prod.yml:84-87`) is gzip-only and its `mime-types` list does **not** include `application/wasm`. Adding it is the concrete change behind "delivered gzipped".
- CSP (`SecurityConfiguration.java:231`): `script-src 'self' 'unsafe-inline' 'unsafe-eval' ...; worker-src 'self' blob:`. `'unsafe-eval'` already permits WASM instantiation and `worker-src 'self' blob:` permits module workers, so **no CSP change is required** for self-hosting.
- Service worker exists: `ngsw-config.json` + `ServiceWorkerModule.register('ngsw-worker.js', {enabled:true})` (`app.config.ts:58`); ngsw asset groups hash their files, so adding the WASM caches it durably and auto-busts on change.
- Background-load precedent: `requestIdleCallback` with a timeout in `landing-spotlight.component.ts`.

## Architecture

### 1. Shared EmbedPDF engine layer (new, used by both clusters)
- Deps: `@embedpdf/core`, `@embedpdf/engines`, `@embedpdf/pdfium`, the viewer plugins used (`plugin-render`, `plugin-scroll`, `plugin-viewport`, `plugin-zoom`, `plugin-search`, `plugin-selection`, `plugin-thumbnail`, `plugin-fullscreen`, `plugin-export`, optional `plugin-i18n`), and the required `@embedpdf/fonts-*`.
- `PdfEngineService` (e.g. `core/pdf/pdf-engine.service.ts`): injectable singleton creating the PDFium `WebWorkerEngine` (`@embedpdf/engines/worker`) with `wasmUrl` pointed at the self-hosted asset; exposes the typed `PdfEngine` API. Replaces `PDFNotificationService.onPDFJSInitSignal()` (viewer) and all raw `pdfjs-dist`/`pdf-lib` calls (editor).
- `signalFromStore(store)` helper bridging EmbedPDF's headless plugin store (`getState`/`dispatch`/`subscribe`) onto Angular signals/effects for zoneless change detection (the React/Vue adapters are the reference; there is no Angular adapter).

### 2. WASM delivery (self-host + gzip + background prefetch + cache)
- **Bundle from the server:** add an `angular.json` `assets` entry copying `node_modules/@embedpdf/pdfium/dist/pdfium.wasm` (and required engine-worker/font assets) to output `assets/embedpdf/` with a version-stamped name (e.g. `pdfium-<version>.wasm`); Spring serves it at `/assets/embedpdf/...`. Point the engine `wasmUrl` (and font URLs) there so nothing hits EmbedPDF's default CDN. (Mirrors the monaco `vs/` + pdfjs worker precedent.)
- **Gzipped over the wire:** add `application/wasm` to `server.compression.mime-types` in `application-prod.yml` and `application-dev.yml` (the ~4.4 MB binary then ships ~2.0 MB gzipped).
- **Durable cache:** it sits under `/assets/**` (public-cached); add `assets/embedpdf/**` to an `ngsw-config.json` asset group (lazy + `updateMode: prefetch`, or a dedicated prefetch group) so the service worker caches it and revalidates on content-hash change; V8's on-disk WASM code cache makes warm reloads skip recompilation.
- **Background prefetch after first render:** `EmbedPdfPreloadService` invoked from the app shell once render stabilizes, scheduling engine init via `requestIdleCallback` (timeout fallback, mirroring `landing-spotlight.component.ts`). Init runs in the `WebWorkerEngine`, so it fetches (gzipped) and streaming-compiles the WASM off the main thread, warming the HTTP cache, the SW cache, and the V8 code cache before the first PDF open. Gate to authenticated app contexts.

### 3. Viewer migration (drop the iframe)
- Move the viewer + the existing custom toolbar into `PdfViewerComponent`, rendering EmbedPDF directly. **Preserve `PdfViewerComponent`'s public inputs/outputs** (`pdfUrl`, `uploadDate`, `version`, `initialPage`; `currentPageChange`, `pageRendered`, `loadError`, `downloadRequested`, `isFullscreenChange`) so consumers (`attachment-video-unit.component`, `lecture-unit.component` `querySelector('jhi-pdf-viewer')`, `course-lecture-details.component`) are unchanged.
- Re-point feature wiring from pdf.js `eventBus`/`PDFNotificationService` to plugins:
  - render + text layer -> `plugin-render` (`RenderLayer`) + `plugin-selection` (`SelectionLayer`).
  - search -> `plugin-search` (`searchAllPages`, `nextResult`/`previousResult`, count from state `total`/`activeResultIndex`); highlight overlay from `SearchResult.rects`.
  - zoom -> `plugin-zoom` (`zoomIn`/`zoomOut`/`requestZoom(ZoomMode.FitWidth)`), preserving the scroll re-centering math.
  - page nav -> `plugin-scroll`/`plugin-viewport` (`scrollToPage`, `state.currentPage`/`totalPages`).
  - dark mode -> app-side CSS `filter: invert(1) hue-rotate(180deg)` on the render layer (no native invert) plus the existing toolbar CSS-variable theming.
  - i18n -> keep `TranslateService` for the Artemis toolbar (headless = app owns all UI text; `plugin-i18n` optional and ships no de/en).
  - fullscreen -> handle directly in `PdfViewerComponent` (it already owns the `.pdf-fullscreen-window` overlay) or `plugin-fullscreen`.
  - download -> emit `downloadRequested` (unchanged) or `plugin-export`.
- Re-point SCSS from pdf.js DOM ids (`#viewerContainer`, `.pdfViewer`, `#toolbarContainer`, `.textLayer .highlight`, `.page`) to EmbedPDF's layers; keep the 5-level toolbar compression.
- **Remove** `pdf-viewer-iframe-content.component.*`, `pdf-viewer-iframe.types.ts`, the `pdf-viewer-iframe` route in `app.routes.ts`, and the three `url.includes('/pdf-viewer-iframe')` special-cases (`app.component.ts`, `artemis-version.interceptor.ts`, `course-notification-websocket.service.ts`).

### 4. Editor migration (`lecture/manage/pdf-preview/`)
- Replace pdfjs thumbnail rendering with `PdfEngineService`: open each source PDF as its own `PdfDocumentObject` (cached by id) and render pages with `renderPageRaw`/`renderThumbnailRaw` (RGBA `ImageDataLike` -> `putImageData` onto the existing `<canvas>`). The `pdf-preview-enlarged-canvas` `drawImage` pixel-reuse stays.
- Replace pdf-lib mutation with the engine API. **Note the model is rebuild, not in-place** (EmbedPDF has no `movePage`):
  - merge -> `importPages(destDoc, srcDoc, indices, insertIndex)`; delete -> `deletePage`; reorder -> rebuild a fresh working doc via `createDocument` + `importPages` in target order (or `mergePages([{docId, pageIndices}])` with an ordered index list).
  - Two outputs from one session: instructor = `saveAsCopy(workingDoc) → ArrayBuffer`; student (hidden pages removed) = `extractPages(workingDoc, visibleIndices) → ArrayBuffer`. This is cleaner than today's "load instructor bytes into a second doc then removeHiddenPages".
- Rewrite the shared interfaces in `pdf-preview.component.ts`: `PDFSource` (drop pdf-lib `PDFDocument` -> `PdfDocumentObject` handle), `OrderedPage` (drop `PDFJS.PDFPageProxy` -> a `(docId, pageObject)` ref). Update `pdf-preview-thumbnail-grid.component.ts` and `pdf-preview-date-box.component.ts` imports. Keep the `MERGE/DELETE/REORDER/HIDE/SHOW` timestamped replay, `slideToPageMap`, and the instructor/student split in `applyOperations`.
- **Keep the server upload contracts byte-faithful** (server APIs): `AttachmentService.update` (PUT `/api/lecture/attachments/{id}`, FormData `attachment`+`file`), `AttachmentVideoUnitService.update` (FormData `file`/`attachment`/`attachmentVideoUnit`/`pageOrder`/`hiddenPages`), `updateStudentVersion`. Only the bytes' producer changes.
- Per-page rotation is **not** an EmbedPDF write capability, but Artemis does not persist per-page rotation today, so no regression.

### 5. Cleanup
- `package.json`: remove `ngx-extended-pdf-viewer`, `pdfjs-dist`, `pdf-lib`; drop the `pdfjs_copy_worker_script.mjs` postinstall; add the `@embedpdf/*` packages.
- Delete `pdfjs_copy_worker_script.mjs`, the `.gitignore:167` and `eslint.config.mjs:109` entries for the copied worker.
- `angular.json`: remove the `ngx-extended-pdf-viewer/assets` glob; add the `@embedpdf/pdfium` asset entry. Remove `pdfDefaultOptions.assetsFolder` usage.

## Critical files

| Area | Files |
|---|---|
| New engine layer | `core/pdf/pdf-engine.service.ts`, the signal-store bridge helper, `core/pdf/embed-pdf-preload.service.ts` |
| Viewer | `lecture/shared/pdf-viewer/pdf-viewer.component.{ts,html,scss}` (becomes the host); delete `pdf-viewer-iframe-content.component.*` + `pdf-viewer-iframe.types.ts`; `app.routes.ts`, `app.component.ts`, `artemis-version.interceptor.ts`, `course-notification-websocket.service.ts` |
| Editor | `lecture/manage/pdf-preview/pdf-preview.component.ts`, `.../pdf-preview-thumbnail-grid/...`, `.../pdf-preview-enlarged-canvas/...`, `.../pdf-preview-date-box/...`; delete `pdfjs_copy_worker_script.mjs` |
| Build/serve | `angular.json`, `package.json`, `application-prod.yml` + `application-dev.yml`, `ngsw-config.json`, `.gitignore`, `eslint.config.mjs` |
| Specs (rewrite, ~5,100 lines) | `pdf-viewer*.spec.ts` (2), `pdf-preview*.spec.ts` (4), and trim the transitive `vi.mock('pdfjs-dist...')` in `attachment-video-unit.component.spec.ts` |

## Build order (single PR)
0. **Save** `EMBEDPDF_MIGRATION_PLAN.md` at the repo root (uncommitted, for reference).
1. **Spike:** thin zoneless Angular wrapper over `@embedpdf/core` + `WebWorkerEngine` that renders + searches + zooms a real PDF; and a `saveAsCopy`/`extractPages` reorder/delete/merge round-trip on a real Artemis lecture deck to confirm PDFium output fidelity vs pdf-lib. Gate the rest of the work on this.
2. Engine layer + WASM self-hosting/gzip/cache + background prefetch.
3. Viewer (drop iframe); consumers unchanged.
4. Editor (render + mutation port); upload contracts unchanged.
5. Remove the three old libraries + cleanup + spec rewrites.

## Verification
- Static gates: `pnpm run webapp:prod` (AOT), `pnpm exec tsc -p tsconfig.app.json --noEmit`, `pnpm run lint`, `pnpm exec vitest run` on rewritten specs plus new tests for the signal-store bridge and the editor mutation port (reorder/delete/merge/hide produce correct instructor and student page sets).
- Manual / `run` skill (no PDF Playwright E2E exists; headless-fullscreen caveat applies): open a lecture PDF and exercise render, **text selection (and a screen-reader/accessibility sanity check, since the text layer is coordinate-based)**, search (next/prev + count + highlight), zoom + re-centering, page nav, dark mode, fullscreen; then in the editor do merge + delete + reorder + hide, save, reopen, and confirm the instructor and student PDFs are correct.
- WASM delivery: confirm `/assets/embedpdf/pdfium-*.wasm` is served with `Content-Encoding: gzip` (~2 MB), prefetched during idle after first render (off the PDF route's critical path), `200` from SW/disk cache on reload, and not blocked by CSP.

## Risks
- **No Angular adapter** -> in-house signal bridge; EmbedPDF's API moves fast (~30 version-locked plugin packages), so pin exact versions and add bridge tests. Track upstream Angular PR #624.
- **PDFium save fidelity** vs pdf-lib for reorder/delete/merge + hidden-page outputs on real decks: the single highest-impact unknown; the Phase 1 spike settles it.
- **Non-semantic text layer**: text selection is coordinate-based overlays, so accessibility/screen-reader support is weaker than pdf.js's DOM text layer; validate during the spike and decide if acceptable for lectures. Search also lacks regex/fuzzy and has unverified ligature/hyphenation behavior.
- Reorder is a rebuild, not an in-place mutation (more editor-pipeline rewrite than a 1:1 swap).
- Single-maintainer dependency (bus factor); ~2 MB first-load WASM (mitigated by self-hosting + gzip + background prefetch + SW/HTTP/V8 caching).
