import { FRAME_SCRIPT, GENERATION_PLACEHOLDER } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-script';

/**
 * Runs the real sandboxed-frame script against a jsdom document, so its behaviour can be asserted in Vitest.
 *
 * jsdom implements neither iframe sandboxing nor layout, so the isolation itself and the height reporting can
 * only be verified in a browser (see the Playwright specs). What *can* be verified here is everything the script
 * decides: which click becomes which message, which task gets `role` / `tabindex` / `aria-label`, and that a
 * message from anyone other than the parent is ignored.
 *
 * The script is executed as the string that actually ships, rather than a re-implementation of it, so a spec
 * that passes here is a statement about the deployed artifact.
 */
export interface FrameHarness {
    /** The document the script is running against. */
    document: Document;
    /** That document's window, which is what makes focus and event constructors behave as they do in a browser. */
    view: Window & typeof globalThis;
    /** Removes the frame again. */
    destroy(): void;
    /** Every message the script posted to its parent, in order. */
    posted: Record<string, unknown>[];
    /** Delivers a message to the script as if the parent had sent it. */
    sendFromParent(data: unknown): void;
    /** Delivers a message from someone who is not the parent, which the script must ignore. */
    sendFromStranger(data: unknown): void;
}

export const FRAME_TEST_GENERATION = 'test-generation';

/**
 * @param bodyHtml the statement markup to put in the frame's body
 */
export function runFrameScript(bodyHtml: string): FrameHarness {
    const posted: Record<string, unknown>[] = [];

    // A real iframe rather than a detached document: the script relies on things only a browsing context
    // provides, above all `activeElement` after `focus()` and the event constructors it is dispatched with.
    const element = document.createElement('iframe');
    document.body.appendChild(element);
    const frameDocument = element.contentDocument!;
    const view = element.contentWindow! as Window & typeof globalThis;
    frameDocument.body.innerHTML = bodyHtml;

    const listeners: ((event: MessageEvent) => void)[] = [];
    const parentStub = { postMessage: (message: Record<string, unknown>) => posted.push(message) };

    // The seam is small on purpose: `parent` is where the script reports, `window` is where it listens, and the
    // rest is the frame's own document. ResizeObserver is deliberately withheld, which is the branch jsdom takes
    // anyway, since it has no layout for one to observe.
    const scope = {
        parent: parentStub,
        document: frameDocument,
        window: {
            addEventListener: (type: string, listener: (event: MessageEvent) => void) => {
                if (type === 'message') {
                    listeners.push(listener);
                }
            },
        },
        ResizeObserver: undefined,
    };

    new Function(...Object.keys(scope), FRAME_SCRIPT.replace(GENERATION_PLACEHOLDER, FRAME_TEST_GENERATION))(...Object.values(scope));

    const deliver = (data: unknown, source: unknown) => {
        listeners.forEach((listener) => listener({ data, source } as MessageEvent));
    };

    return {
        document: frameDocument,
        view,
        posted,
        sendFromParent: (data) => deliver(data, parentStub),
        sendFromStranger: (data) => deliver(data, { postMessage: () => undefined }),
        destroy: () => element.remove(),
    };
}

/** Builds the `interactive` message the parent component sends, for the given task indices. */
export function interactiveMessage(entries: { index: number; label: string }[]): Record<string, unknown> {
    return { type: 'interactive', tasks: entries };
}
