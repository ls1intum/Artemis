import { Component, ElementRef, EventEmitter, Input, OnInit, Output, Renderer2 } from '@angular/core';
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
    colorSelectorPosition: Coordinates;
    showColorSelector = false;
    height = 220;
    @Input() tagColors: string[] = DEFAULT_COLORS;
    @Output() selectedColor = new EventEmitter<string>();

    constructor(
        private elementRef: ElementRef,
        private renderer: Renderer2,
    ) {}

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

                const isClickOutsideOfComponent = this.elementRef.nativeElement.parentElement !== target;
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
        const parentElement = (event.target as Element).closest('.ng-trigger') as HTMLElement;

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
        this.selectedColor.emit(selectedColor);
        this.showColorSelector = false;
    }

    /**
     * close colorSelector
     */
    cancelColorSelector() {
        this.showColorSelector = false;
    }
}
