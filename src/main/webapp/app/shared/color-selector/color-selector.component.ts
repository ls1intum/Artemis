import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
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
    @Input() tagColors: string[] = DEFAULT_COLORS;
    @Output() selectedColor = new EventEmitter<string>();

    ngOnInit(): void {
        this.colorSelectorPosition = { left: 0, top: 0 };
    }

    openColorSelector(event: MouseEvent) {
        const parentElement = (event.target as Element).closest('.ng-trigger') as HTMLElement;
        this.colorSelectorPosition.left = parentElement ? parentElement.offsetLeft : 0;
        this.colorSelectorPosition.top = 65;
        this.showColorSelector = true;
    }

    selectColorForTag(selectedColor: string) {
        this.showColorSelector = false;
        this.selectedColor.emit(selectedColor);
    }

    cancelColorSelector() {
        this.showColorSelector = false;
    }
}
