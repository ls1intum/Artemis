import { Directive, ElementRef, EventEmitter, NgZone, OnDestroy, OnInit, Output, inject } from '@angular/core';

/**
 * Disclaimer:
 * This is based on https://github.com/bennadel/JavaScript-Demos/blob/master/demos/selection-range-directive-angular5/app/text-select.directive.ts
 */

export interface TextSelectEvent {
    text: string;
    viewportRectangle: SelectionRectangle | null;
    hostRectangle: SelectionRectangle | null;
}

export interface SelectionRectangle {
    left: number;
    top: number;
    width: number;
    height: number;
}

/**
 * Enum for different event types of text select directive.
 * @type {{SelectionChange: string, MouseUp: string, MouseDown: string}}
 */
enum EventType {
    SelectionChange = 'selectionchange',
    MouseUp = 'mouseup',
    MouseDown = 'mousedown',
}

@Directive({
    selector: '[jhiTextSelect]',
})
export class TextSelectDirective implements OnInit, OnDestroy {
    private elementRef = inject(ElementRef);
    private zone = inject(NgZone);

    @Output()
    public jhiTextSelect = new EventEmitter<TextSelectEvent>();
    private hasSelection = false;

    /**
     * Init text select directive by adding event listenes mouseDown and selectionChange event listeners to element.
     */
    public ngOnInit(): void {
        // Since not all interactions will lead to an event that is meaningful to the
        // calling context, we want to setup the DOM bindings outside of the Angular
        // Zone. This way, we don't trigger any change-detection digests until we know
        // that we have a computed event to emit.
        this.zone.runOutsideAngular(() => {
            // While there are several ways to create a selection on the page, this
            // directive is only going to be concerned with selections that were
            // initiated by MOUSE-based selections within the current element.
            this.elementRef.nativeElement.addEventListener(EventType.MouseDown, this.handleMouseDown, false);

            // While the mouse-even takes care of starting new selections within the
            // current element, we need to listen for the selectionchange event in
            // order to pick-up on selections being removed from the current element.
            document.addEventListener(EventType.SelectionChange, this.handleSelectionChange, false);
        });
    }

    /**
     * Unbind all event listeners on destruction.
     */
    public ngOnDestroy(): void {
        // Unbind all handlers, even ones that may not be bounds at this moment.
        this.elementRef.nativeElement.removeEventListener(EventType.MouseDown, this.handleMouseDown, false);
        document.removeEventListener(EventType.MouseUp, this.handleMouseUp, false);
        document.removeEventListener(EventType.SelectionChange, this.handleSelectionChange, false);
    }

    // I get the deepest Element node in the DOM tree that contains the entire range.
    private getRangeContainer(range: Range): Node {
        let container = range.commonAncestorContainer;

        // If the selected node is a Text node, climb up to an element node - in Internet
        // Explorer, the .contains() method only works with Element nodes.
        while (container.nodeType !== Node.ELEMENT_NODE) {
            container = container.parentNode!;
        }

        return container;
    }

    // I handle mousedown events inside the current element.
    private handleMouseDown = (): void => {
        document.addEventListener(EventType.MouseUp, this.handleMouseUp, false);
    };

    // I handle mouseup events anywhere in the document.
    private handleMouseUp = (): void => {
        document.removeEventListener(EventType.MouseUp, this.handleMouseUp, false);
        this.processSelection();
    };

    // I handle selectionchange events anywhere in the document.
    private handleSelectionChange = (): void => {
        // We are using the mousedown / mouseup events to manage selections that are
        // initiated from within the host element. But, we also have to account for
        // cases in which a selection outside the host will cause a local, existing
        // selection (if any) to be removed. As such, we'll only respond to the generic
        // 'selectionchange' event when there is a current selection that is in danger
        // of being removed.
        if (this.hasSelection) {
            this.processSelection();
        }
    };

    // I inspect the document's current selection and check to see if it should be
    // emitted as a TextSelectEvent within the current element.
    private processSelection(): void {
        const selection = document.getSelection();

        // If there is a new selection and an existing selection, let's clear out the
        // existing selection first.
        if (this.hasSelection) {
            // Since emitting event may cause the calling context to change state, we
            // want to run the .emit() inside of the Angular Zone. This way, it can
            // trigger change detection and update the views.
            this.zone.runGuarded(() => {
                this.hasSelection = false;
                this.jhiTextSelect.emit({
                    text: '',
                    viewportRectangle: null,
                    hostRectangle: null,
                });
            });
        }

        // If the new selection is empty (for example, the user just clicked somewhere
        // in the document), then there's no new selection event to emit.
        if (!selection || !selection.rangeCount || !selection.toString()) {
            return;
        }

        const range = selection.getRangeAt(0);
        const rangeContainer = this.getRangeContainer(range);

        // We only want to emit events for selections that are fully contained within the
        // host element. If the selection bleeds out-of or in-to the host, then we'll
        // just ignore it since we don't control the outer portions.
        if (this.elementRef.nativeElement.contains(rangeContainer)) {
            const viewportRectangle = range.getBoundingClientRect();
            const localRectangle = this.viewportToHost(viewportRectangle, rangeContainer);

            // Since emitting event may cause the calling context to change state, we
            // want to run the .emit() inside of the Angular Zone. This way, it can
            // trigger change detection and update the views.
            this.zone.runGuarded(() => {
                this.hasSelection = true;
                this.jhiTextSelect.emit({
                    text: selection.toString(),
                    viewportRectangle: {
                        left: viewportRectangle.left,
                        top: viewportRectangle.top,
                        width: viewportRectangle.width,
                        height: viewportRectangle.height,
                    },
                    hostRectangle: {
                        left: localRectangle.left,
                        top: localRectangle.top,
                        width: localRectangle.width,
                        height: localRectangle.height,
                    },
                });
            });
        }
    }

    // I convert the given viewport-relative rectangle to a host-relative rectangle.
    // --
    // NOTE: This algorithm doesn't care if the host element has a position - it simply
    // walks up the DOM tree looking for offsets.
    private viewportToHost(viewportRectangle: SelectionRectangle, rangeContainer: Node): SelectionRectangle {
        const host = this.elementRef.nativeElement;
        const hostRectangle = host.getBoundingClientRect();

        // Both the selection rectangle and the host rectangle are calculated relative to
        // the browser viewport. As such, the local position of the selection within the
        // host element should just be the delta of the two rectangles.
        let localLeft = viewportRectangle.left - hostRectangle.left;
        let localTop = viewportRectangle.top - hostRectangle.top;

        let node = rangeContainer;
        // Now that we have the local position, we have to account for any scrolling
        // being performed within the host element. Let's walk from the range container
        // up to the host element and add any relevant scroll offsets to the calculated
        // local position.
        while (node !== host && node.parentNode != undefined) {
            localLeft += (<Element>node).scrollLeft;
            localTop += (<Element>node).scrollTop;
            node = node.parentNode;
        }

        return {
            left: localLeft,
            top: localTop,
            width: viewportRectangle.width,
            height: viewportRectangle.height,
        };
    }
}
