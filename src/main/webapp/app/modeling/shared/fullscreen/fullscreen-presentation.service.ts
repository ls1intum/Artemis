import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Marks the element that a component promoted to `<body>` for document-root fullscreen. Global rules key the stacking
 * of body- and html-level portals (Apollon popovers, CDK overlays) on it, so both Apollon surfaces must carry the
 * same class rather than each inventing its own.
 */
export const APOLLON_FULLSCREEN_FRAME_CLASS = 'apollon-fullscreen-frame';

/** True when the element still produces a box, i.e. no ancestor hides it. */
function isSlotStillShown(element: Element): boolean {
    // `checkVisibility` is the precise answer but is not implemented everywhere (jsdom included); the client-rect
    // fallback catches exactly the `display: none` case this guard exists for.
    const withCheckVisibility = element as Element & { checkVisibility?: (options?: unknown) => boolean };
    if (typeof withCheckVisibility.checkVisibility === 'function') {
        return withCheckVisibility.checkVisibility();
    }
    return element.isConnected && element.getClientRects().length > 0;
}

/**
 * Owns a single body promotion; restoration removes the element if its original view no longer exists.
 *
 * Promotion re-parents the frame to `<body>`, which takes it out of reach of everything its original ancestors do to
 * it. Two hosts rely on exactly that reach and would otherwise strand a fullscreen frame over the whole app:
 *
 * - the exam page switcher hides the previous exercise with `[hidden]` on a wrapper the frame no longer descends from
 *   (`exam-participation.component.html`), so switching exercises left the old editor pinned fullscreen and
 *   intercepting every click;
 * - a `readOnly` flip destroys the frame's `@if` block, removing the element from `<body>` while the document stays
 *   fullscreen with nothing in it.
 *
 * The promotion therefore watches for both and calls back so the owner can stand down.
 */
@Injectable({ providedIn: 'root' })
export class FullscreenPresentationService {
    private readonly document = inject(DOCUMENT);
    private promotedElement?: HTMLElement;
    private restoreAnchor?: Comment;
    private escapeObservers: { disconnect(): void }[] = [];

    owns(element: HTMLElement | undefined): boolean {
        return element !== undefined && this.promotedElement === element;
    }

    /**
     * @param element the frame to show fullscreen
     * @param onEscape called when the promotion stops being legitimate — the original slot got hidden, or the element
     *                 was destroyed underneath us. The owner is expected to restore and leave fullscreen.
     */
    promote(element: HTMLElement, onEscape?: () => void): boolean {
        if (this.promotedElement) {
            return false;
        }

        this.restoreAnchor = this.document.createComment('fullscreen-presentation-anchor');
        element.before(this.restoreAnchor);
        this.promotedElement = element;
        this.document.body.append(element);
        if (onEscape) {
            this.watchForEscape(element, onEscape);
        }
        return true;
    }

    restore(): void {
        const element = this.promotedElement;
        const anchor = this.restoreAnchor;
        this.stopWatching();
        if (!element || !anchor) {
            return;
        }

        // A destroyed frame must not be resurrected into the view that just dropped it.
        if (anchor.parentNode && element.isConnected) {
            anchor.parentNode.insertBefore(element, anchor.nextSibling);
        } else {
            element.remove();
        }
        anchor.remove();
        this.promotedElement = undefined;
        this.restoreAnchor = undefined;
    }

    private watchForEscape(element: HTMLElement, onEscape: () => void): void {
        const escapeOnce = () => {
            if (this.promotedElement === element) {
                onEscape();
            }
        };

        // The frame is a direct child of <body> while promoted, so a shallow childList watch is enough to notice
        // Angular tearing it out from under us, and costs nothing while it stays.
        if (typeof MutationObserver !== 'undefined') {
            const removalObserver = new MutationObserver(() => {
                if (!element.isConnected) {
                    escapeOnce();
                }
            });
            removalObserver.observe(this.document.body, { childList: true });
            this.escapeObservers.push(removalObserver);
        }

        // The slot the frame came from still sits in the original view, so it still reacts to whatever hides that
        // view. An IntersectionObserver fires when it stops producing a box — including via an ancestor's `[hidden]`.
        const slot = this.restoreAnchor?.parentElement;
        if (slot && typeof IntersectionObserver !== 'undefined') {
            const hiddenObserver = new IntersectionObserver(() => {
                // Intersection alone would also mean "merely scrolled out of view", which is not an escape.
                if (!isSlotStillShown(slot)) {
                    escapeOnce();
                }
            });
            hiddenObserver.observe(slot);
            this.escapeObservers.push(hiddenObserver);
        }
    }

    private stopWatching(): void {
        for (const observer of this.escapeObservers) {
            observer.disconnect();
        }
        this.escapeObservers = [];
    }
}
