import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

export const APOLLON_FULLSCREEN_FRAME_CLASS = 'apollon-fullscreen-frame';

@Injectable({ providedIn: 'root' })
export class FullscreenPresentationService {
    private readonly document = inject(DOCUMENT);
    private frame?: HTMLElement;
    private anchor?: Comment;
    private slot?: HTMLElement;
    private escapeObservers: { disconnect(): void }[] = [];

    owns(element: HTMLElement | undefined): boolean {
        return element !== undefined && this.frame === element;
    }

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

        const removalObserver = new MutationObserver(() => {
            if (!element.isConnected) {
                escapeOnce();
            }
        });
        removalObserver.observe(this.document.body, { childList: true });
        this.escapeObservers.push(removalObserver);

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
