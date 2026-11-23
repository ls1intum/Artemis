# Download cases

A native Save dialog is not a JavaScript alert or an upload picker. `handle_dialog`, DOM scripting, and `upload_file` cannot operate it. Plan download cases before clicking: name the expected filename or extension, format, and content, and choose a capture method that works with the required role and session.

## Capture the UI download

1. Use chrome-devtools MCP as the primary browser. Before the export, check whether this browser can save and expose downloads without a native dialog. If it can, drive the export there and inspect the saved file. Do not sign out, change the user's browser settings, or export their session.
2. If the primary browser cannot capture the file unattended, use an isolated Playwright context for this download case as a fallback, and only if the required role can sign in there without copying the user's session or entering personal credentials. Keep chrome-devtools as the primary browser for the other cases. Record that the capture used a different browser session. The recorder harness exposes `page` and `context`; its `startSession` creates a fresh context. Register the download listener **before** the real UI click, save before closing the context, and inspect the saved file. Example using the harness's `humanClick`:

   ```js
   const [download] = await Promise.all([
     page.waitForEvent('download', { timeout: 15000 }),
     humanClick(page, downloadButton),
   ]);
   const suggestedName = download.suggestedFilename();
   await download.saveAs(absoluteEvidencePath); // a unique, trusted path in the run dir
   const failure = await download.failure();
   if (failure) throw new Error(`Download failed: ${failure}`);
   ```

   Enable `acceptDownloads: true` when creating a context directly. Use a fixed case-specific path, not the untrusted suggested filename as a path. Record the suggested name separately. This covers browser-generated blobs as well as server attachments. If a popup owns the download, listen on that page instead. A download event alone is not proof of correct content.
3. If neither automated route is available, ask the user to handle the native Save dialog and save to a named private evidence path. If a click is already waiting on the dialog, pause and ask; do not retry or assume `handle_dialog` can close it. If the user cannot help, mark the case blocked and continue other cases when safe. In every capture path, wait for completion. Check the actual file exists, is not partial, and matches the expected type and case-specific content; inspect archive members or parse the document where relevant. Record the UI action, suggested filename, size, format and content assertions, and any request status and headers available without exposing credentials. A server response or a direct API fetch alone is not proof that the UI download saved successfully. An independently fetched response can supplement, not replace, UI evidence.

For a CSV export, parse the saved file with a CSV parser, not `split(',')`. Check the selected headers, omitted fields, row count and representative values against the fixture, including quoting and special characters where relevant. When the client builds the file itself (a blob behind a detached link), the HTTP response alone does not prove the browser saved it; trace the export code to see which.

A missing event, dialog, saved file, or role is blocked when the tooling or environment prevents observation; an observed wrong name, corrupt file, or wrong content is a failure. Do not re-trigger a state-changing export blindly. If testing the native dialog itself is required, an automated capture does not prove that dialog works; ask for human verification or mark it untested.

Keep downloaded test data in the private run dir. Do not upload or publish it without checking for sensitive data and obtaining any required approval. Remove temporary test files after inspection, or record the retained evidence and cleanup.
