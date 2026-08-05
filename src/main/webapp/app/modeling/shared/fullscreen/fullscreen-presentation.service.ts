import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/** Owns a single body promotion; restoration removes the element if its original view no longer exists. */
@Injectable({ providedIn: 'root' })
export class FullscreenPresentationService {
    private readonly document = inject(DOCUMENT);
    private promotedElement?: HTMLElement;
    private restoreAnchor?: Comment;

    owns(element: HTMLElement | undefined): boolean {
        return element !== undefined && this.promotedElement === element;
    }

    promote(element: HTMLElement): boolean {
        if (this.promotedElement) {
            return false;
        }

        this.restoreAnchor = this.document.createComment('fullscreen-presentation-anchor');
        element.before(this.restoreAnchor);
        this.promotedElement = element;
        this.document.body.append(element);
        return true;
    }

    restore(): void {
        const element = this.promotedElement;
        const anchor = this.restoreAnchor;
        if (!element || !anchor) {
            return;
        }

        if (anchor.parentNode) {
            anchor.parentNode.insertBefore(element, anchor.nextSibling);
        } else {
            element.remove();
        }
        anchor.remove();
        this.promotedElement = undefined;
        this.restoreAnchor = undefined;
    }
}
