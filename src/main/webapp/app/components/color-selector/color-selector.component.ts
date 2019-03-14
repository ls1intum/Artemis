import { Component, Output, EventEmitter, Input, OnInit } from '@angular/core';

export interface Coordinates {
    left: number;
    top: number;
}

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-color-selector',
    templateUrl: './color-selector.component.html',
    styleUrls: ['./color-selector.scss']
})

export class ColorSelectorComponent implements OnInit {
    colorSelectorPosition: Coordinates;
    showColorSelector = false;
    @Input() tagColors: string[] = DEFAULT_COLORS;
    @Output() selectedColor = new EventEmitter<string>();

    ngOnInit(): void {
        this.colorSelectorPosition = {left: 0, top: 0};
    }

    openColorSelector(event: any) {
        const parentElement = event.target.closest('.ng-trigger');
        this.colorSelectorPosition.left = parentElement ? parentElement.offsetLeft : 0;
        this.colorSelectorPosition.top = 65;
        this.showColorSelector = true;
    }

    selectColorForTag(selectedColor: string) {
        this.selectedColor.emit(selectedColor);
        this.showColorSelector = false;
    }

    cancelColorSelector() {
        this.showColorSelector = false;
    }
}
