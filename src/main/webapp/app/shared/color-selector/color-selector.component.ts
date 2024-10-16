import { Component, ElementRef, EventEmitter, Input, OnInit, Output, Renderer2, inject } from '@angular/core';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

export interface Coordinates {
    left: number;
    top: number;
}

const DEFAULT_COLORS = [
    ARTEMIS_DEFAULT_COLOR,
    '#1b97ca',
    '#0d3cc2',
    '#009999',
    '#0ab84f',
    '#94a11c',
    '#9dca53',
    '#ffd014',
    '#c6aa1c',
    '#ffa500',
    '#ffb2b2',
    '#ca94bd',
    '#a95292',
    '#691b0b',
    '#ad5658',
    '#ff1a35',
];

@Component({
    selector: 'jhi-color-selector',
    templateUrl: './color-selector.component.html',
    styleUrls: ['./color-selector.scss'],
})
export class ColorSelectorComponent implements OnInit {
    private elementRef = inject(ElementRef);
    private renderer = inject(Renderer2);

    colorSelectorPosition: Coordinates;
    showColorSelector = false;
    height = 220;
    @Input() tagColors: string[] = DEFAULT_COLORS;
    @Output() selectedColor = new EventEmitter<string>();

    /**
     * set default position on init
     */
    ngOnInit(): void {
        this.colorSelectorPosition = { left: 0, top: 0 };

        this.addEventListenerToCloseComponentOnClickOutside();
    }

    private addEventListenerToCloseComponentOnClickOutside() {
        this.renderer.listen('document', 'click', (event: Event) => {
            if (this.showColorSelector) {
                const target = event.target as HTMLElement;

                const isClickOutsideOfComponent = !this.elementRef.nativeElement.contains(target);
                if (isClickOutsideOfComponent) {
                    this.showColorSelector = false;
                }
            }
        });
    }

    /**
     * open colorSelector and position correctly
     * @param {MouseEvent} event
     * @param {number} marginTop
     * @param {number} height
     */
    openColorSelector(event: MouseEvent, marginTop?: number, height?: number) {
        /**
         * without {@link event#stopPropagation} the color picker would close immediately as the mouseEvent
         * is triggered again for the child component {@link ColorSelectorComponent} which would interpret
         * it as a click outside the colorpicker
         */
        event.stopPropagation();
        const clickedElement = event.target as HTMLElement;
        const parentElement = clickedElement.closest('.category-chip') as HTMLElement;

        this.colorSelectorPosition.left = parentElement ? parentElement.offsetLeft : 0;
        this.colorSelectorPosition.top = marginTop ?? 65;
        if (height !== undefined) {
            this.height = height;
        }

        this.showColorSelector = !this.showColorSelector;
    }

    /**
     * close colorSelector and emit it to be set
     * @param {string} selectedColor
     */
    selectColorForTag(selectedColor: string) {
        this.showColorSelector = false;
        this.selectedColor.emit(selectedColor);
    }

    /**
     * close colorSelector
     */
    cancelColorSelector() {
        this.showColorSelector = false;
    }
}
