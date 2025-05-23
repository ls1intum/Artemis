import { Component, input, model } from '@angular/core';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
})
export class DoubleSliderComponent {
    title = input.required<string>();
    initialValue = input.required<number>();
    min = input<number>(1);
    max = input<number>(5);
    step = input<number>(1);

    currentValue = model.required<number>();

    onChange(event: Event) {
        this.currentValue.set(parseInt((event.currentTarget as HTMLInputElement).value));
    }
}
