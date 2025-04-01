import { Component, ElementRef, input, model, viewChild } from '@angular/core';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
})
export class DoubleSliderComponent {
    title = input.required<string>();
    initialValue = input.required<number>();
    min = input.required<number>();
    max = input.required<number>();

    currentValue = model.required<number>();

    currentSlider = viewChild.required<ElementRef>('currentSlider');

    onChange(event: Event) {
        this.currentValue.set(parseInt((event.currentTarget as HTMLInputElement).value));
    }
}
