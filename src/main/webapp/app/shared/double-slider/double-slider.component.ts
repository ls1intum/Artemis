import { Component, input, model } from '@angular/core';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
})
export class DoubleSliderComponent {
    title = input.required<string>();
    description = input<string>('');
    initialValue = input.required<number>();
    min = input.required<number>();
    max = input.required<number>();
    minLabel = input<string>('');
    maxLabel = input<string>('');

    currentValue = model.required<number>();

    onChange(event: Event) {
        this.currentValue.set(parseInt((event.currentTarget as HTMLInputElement).value));
    }
}
