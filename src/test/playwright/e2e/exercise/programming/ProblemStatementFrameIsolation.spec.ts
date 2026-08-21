import { expect } from '@playwright/test';
import { test } from '../../../support/fixtures';
import { contentSecurityPolicy, FRAME_SANDBOX } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-policy';

/**
 * The isolation contract of the sandboxed problem-statement frame, exercised in every engine Artemis supports.
 *
 * These assertions cannot live in Vitest: jsdom implements neither iframe sandboxing nor CSP, so it would report
 * every one of them as passing whatever the code did. They also cannot live in a Chromium-only project, because
 * the guarantees genuinely differ between engines. The measurement that made this file necessary: with the frame
 * CSP removed, WebKit sends the `SameSite=Lax` JWT cookie on requests issued from a sandboxed opaque-origin
 * frame, while Chromium and Firefox send none. The CSP is therefore load-bearing rather than defence in depth,
 * and a future change that relaxes it would be invisible to a Chromium-only suite while silently handing Safari
 * users a blind CSRF channel.
 *
 * The frame is built here from the same exported policy and sandbox the application uses, so relaxing either in
 * production code fails these tests rather than quietly passing them.
 *
 * The page is loaded from the Artemis origin (unauthenticated is enough: what matters is that the browser is on
 * the real origin, with the real response headers and cookie jar) and the frame is injected into it, exactly as
 * the content component does.
 */
test.describe('Problem statement frame isolation @cross-engine', () => {
    const NONCE = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

    /** The frame the application would build, with an injected payload standing in for a sanitizer bypass. */
    const frameDocument = (origin: string, injected: string): string =>
        `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">` +
        `<meta http-equiv="Content-Security-Policy" content="${contentSecurityPolicy(NONCE, origin)}">` +
        `</head><body>${injected}` +
        `<script nonce="${NONCE}">
            const probe = {};
            probe.attackerRan = !!window.__attackerRan;
            probe.origin = String(window.origin);
            try { probe.cookie = document.cookie === '' ? 'EMPTY' : 'SENT:' + document.cookie; } catch (e) { probe.cookie = 'THROWS:' + e.name; }
            try { probe.storage = String(localStorage.length); } catch (e) { probe.storage = 'THROWS:' + e.name; }
            try { probe.parentDom = String(parent.document.title); } catch (e) { probe.parentDom = 'THROWS:' + e.name; }
            try { top.location = '${origin}/about'; probe.topNav = 'ALLOWED'; } catch (e) { probe.topNav = 'THROWS:' + e.name; }
            try { probe.popup = window.open('${origin}/about') ? 'OPENED' : 'NULL'; } catch (e) { probe.popup = 'THROWS:' + e.name; }
            fetch('${origin}/api/core/public/account', { credentials: 'include' })
                .then(() => { probe.fetch = 'SENT'; })
                .catch((e) => { probe.fetch = 'BLOCKED'; })
                .finally(() => setTimeout(() => parent.postMessage(probe, '*'), 300));
        </script></body></html>`;

    /** Injects the frame and returns what the trusted script inside it could observe. */
    const probeFrame = async (page: import('@playwright/test').Page, injected: string): Promise<Record<string, string | boolean>> => {
        const origin = new URL(page.url()).origin;
        return await page.evaluate(
            ({ srcdoc, sandbox }) =>
                new Promise<Record<string, string | boolean>>((resolve) => {
                    const frame = document.createElement('iframe');
                    frame.setAttribute('sandbox', sandbox);
                    frame.style.cssText = 'width:600px;height:200px;border:0';
                    window.addEventListener('message', (event) => {
                        if (event.source === frame.contentWindow) {
                            resolve(event.data);
                        }
                    });
                    frame.srcdoc = srcdoc;
                    document.body.appendChild(frame);
                    setTimeout(() => resolve({ timedOut: true }), 5000);
                }),
            { srcdoc: frameDocument(origin, injected), sandbox: FRAME_SANDBOX },
        );
    };

    test.beforeEach(async ({ page }) => {
        await page.goto('/');
    });

    test('denies the statement every way of reaching the user', async ({ page }) => {
        const probe = await probeFrame(page, '<p>a statement</p>');

        // The opaque origin is what all of the below follows from; if this is ever "http(s)://...", the sandbox
        // has gained allow-same-origin and every other guarantee here is void.
        expect(probe.origin).toBe('null');
        expect(String(probe.cookie)).not.toContain('SENT:');
        expect(String(probe.storage)).toContain('THROWS');
        expect(String(probe.parentDom)).toContain('THROWS');
        expect(String(probe.topNav)).toContain('THROWS');
        expect(probe.popup).not.toBe('OPENED');
    });

    test('does not execute an injected script, because it has no nonce', async ({ page }) => {
        // This is the case the whole design exists for: the payload got past the server safelist and DOMPurify.
        const probe = await probeFrame(page, '<p>x</p><script>window.__attackerRan = true;</script>');

        expect(probe.attackerRan).toBe(false);
    });

    test('does not execute an injected event handler either', async ({ page }) => {
        const probe = await probeFrame(page, '<img src="x" onerror="window.__attackerRan = true;">');

        expect(probe.attackerRan).toBe(false);
    });

    test('cannot reach the API even with credentials, which is what closes the Safari cookie gap', async ({ page }) => {
        const probe = await probeFrame(page, '<p>a statement</p>');

        // connect-src 'none'. Without it, WebKit would attach the JWT cookie to this request.
        expect(probe.fetch).toBe('BLOCKED');
    });
});
