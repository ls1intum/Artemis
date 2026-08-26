import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { DomPortal, DomPortalOutlet } from '@angular/cdk/portal';

/**
 * Marks a promoted frame; `global.scss` keys the stacking of body- and html-level portals on it. The editor and
 * assessment templates bind it by name, so it is the value that must match, not the import.
 */
export const APOLLON_FULLSCREEN_FRAME_CLASS = 'apollon-fullscreen-frame';

/** True while no ancestor hides the element. Falls back to client rects where `checkVisibility` is missing (jsdom). */
function isSlotStillShown(element: Element): boolean {
    const withCheckVisibility = element as Element & { checkVisibility?: (options?: unknown) => boolean };
    if (typeof withCheckVisibility.checkVisibility === 'function') {
        return withCheckVisibility.checkVisibility();
    }
    return element.isConnected && element.getClientRects().length > 0;
}

/**
 * Owns a single promotion of a frame to `<body>` for document-root fullscreen.
 *
 * Promotion puts the frame out of reach of whatever its original ancestors do to it, so the promotion watches for the
 * two ways a host can drop it — hiding the original slot, or destroying the frame — and calls back to stand down.
 */
@Injectable({ providedIn: 'root' })
export class FullscreenPresentationService {
    private readonly document = inject(DOCUMENT);
    private portal?: DomPortal<HTMLElement>;
    private outlet?: DomPortalOutlet;
    /** The frame's original parent, captured before the move so the escape watch can tell when that slot is hidden. */
    private slot?: HTMLElement;
    private escapeObservers: { disconnect(): void }[] = [];

    owns(element: HTMLElement | undefined): boolean {
        return element !== undefined && this.portal?.element === element;
    }

    /** @param onEscape run when the original slot is hidden or the frame is destroyed; restore and exit fullscreen. */
    promote(element: HTMLElement, onEscape?: () => void): boolean {
        if (this.portal) {
            return false;
        }

        this.slot = element.parentElement ?? undefined;
        // `detach`, never `dispose`: disposing a DomPortalOutlet removes its outlet element, which here is <body>.
        this.outlet = new DomPortalOutlet(this.document.body);
        this.portal = new DomPortal(element);
        this.outlet.attach(this.portal);
        if (onEscape) {
            this.watchForEscape(element, onEscape);
        }
        return true;
    }

    restore(): void {
        const element = this.portal?.element;
        // The portal swaps the frame back over its anchor. Correct the two cases that leaves: a destroyed frame would
        // be resurrected, and a frame whose view is gone has no anchor left and would strand under <body>.
        const wasDestroyed = !!element && !element.isConnected;
        this.stopWatching();
        this.outlet?.detach();
        if (element && (wasDestroyed || element.parentElement === this.document.body)) {
            element.remove();
        }
        this.outlet = undefined;
        this.portal = undefined;
        this.slot = undefined;
    }

    private watchForEscape(element: HTMLElement, onEscape: () => void): void {
        const escapeOnce = () => {
            if (this.owns(element)) {
                onEscape();
            }
        };

        // Promoted frames are direct children of <body>, so a shallow childList watch catches Angular destroying one.
        if (typeof MutationObserver !== 'undefined') {
            const removalObserver = new MutationObserver(() => {
                if (!element.isConnected) {
                    escapeOnce();
                }
            });
            removalObserver.observe(this.document.body, { childList: true });
            this.escapeObservers.push(removalObserver);
        }

        // The slot stays in the original view, so it still reacts to whatever hides it; intersection is the trigger,
        // `isSlotStillShown` the decision, since scrolling out of view is not an escape.
        const slot = this.slot;
        if (slot && typeof IntersectionObserver !== 'undefined') {
            const hiddenObserver = new IntersectionObserver(() => {
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
