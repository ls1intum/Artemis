import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Marks a promoted frame; `global.scss` keys the stacking of body- and html-level portals on it. The editor and
 * assessment templates bind it by name, so it is the value that must match, not the import.
 */
export const APOLLON_FULLSCREEN_FRAME_CLASS = 'apollon-fullscreen-frame';

/**
 * Owns a single promotion of a frame to `<body>` for document-root fullscreen.
 *
 * Promotion puts the frame out of reach of whatever its original ancestors do to it, so the promotion watches for the
 * two ways a host can drop it — hiding the original slot, or destroying the frame — and calls back to stand down.
 */
@Injectable({ providedIn: 'root' })
export class FullscreenPresentationService {
    private readonly document = inject(DOCUMENT);
    private frame?: HTMLElement;
    /** Holds the frame's place in the host view, so restoring puts it back exactly where it came from. */
    private anchor?: Comment;
    /** The frame's original parent, captured before the move so the escape watch can tell when that slot is hidden. */
    private slot?: HTMLElement;
    private escapeObservers: { disconnect(): void }[] = [];

    owns(element: HTMLElement | undefined): boolean {
        return element !== undefined && this.frame === element;
    }

    /** @param onEscape run when the original slot is hidden or the frame is destroyed; restore and exit fullscreen. */
    promote(element: HTMLElement, onEscape?: () => void): boolean {
        if (this.frame) {
            return false;
        }

        this.slot = element.parentElement ?? undefined;
        this.anchor = this.document.createComment(APOLLON_FULLSCREEN_FRAME_CLASS);
        element.replaceWith(this.anchor);
        this.document.body.append(element);
        this.frame = element;
        if (onEscape) {
            this.watchForEscape(element, onEscape);
        }
        return true;
    }

    restore(): void {
        const { frame, anchor } = this;
        this.stopWatching();
        // Angular may have destroyed either end while the frame was promoted, in which case there is nothing to put
        // back and both nodes have to go, rather than one being reinserted into a view that has moved on.
        if (frame?.isConnected && anchor?.isConnected) {
            anchor.replaceWith(frame);
        } else {
            frame?.remove();
            anchor?.remove();
        }
        this.frame = undefined;
        this.anchor = undefined;
        this.slot = undefined;
    }

    private watchForEscape(element: HTMLElement, onEscape: () => void): void {
        const escapeOnce = () => {
            if (this.owns(element)) {
                onEscape();
            }
        };

        // Angular can destroy the frame while it is promoted and nothing tells this service, so <body> is watched for
        // the removal. Promoted frames are direct children of it, which is why a shallow childList watch suffices.
        const removalObserver = new MutationObserver(() => {
            if (!element.isConnected) {
                escapeOnce();
            }
        });
        removalObserver.observe(this.document.body, { childList: true });
        this.escapeObservers.push(removalObserver);

        // Intersection is only the trigger: the slot scrolling out of view is not an escape, being hidden is.
        const slot = this.slot;
        if (slot) {
            const hiddenObserver = new IntersectionObserver(() => {
                if (!slot.checkVisibility()) {
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
