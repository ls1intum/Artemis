import { Component, input, model } from '@angular/core';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
})
export class DoubleSliderComponent {
    initialValue = input.required<number>();
    min = input<number>(1);
    max = input<number>(5);
    step = input<number>(1);
    min_label = input<string>(String(this.min()));
    max_label = input<string>(String(this.max()));

    currentValue = model.required<number>();

    onChange(event: Event) {
        this.currentValue.set(parseFloat((event.currentTarget as HTMLInputElement).value));
    }
}
