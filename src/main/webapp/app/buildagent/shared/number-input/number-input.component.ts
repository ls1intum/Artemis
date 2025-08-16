import { Component, input, output } from '@angular/core';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-number-input',
    templateUrl: './number-input.component.html',
    styleUrl: './number-input.component.scss',
    imports: [FaIconComponent],
})
export class NumberInputComponent {
    value = input.required<number>();
    minValue = input<number>(Number.MIN_SAFE_INTEGER);
    maxValue = input<number>(Number.MAX_SAFE_INTEGER);
    disabled = input<boolean>(false);
    valueChange = output<number>();

    // Icons
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;

    increment() {
        if (this.value() < this.maxValue() && !this.disabled()) {
            const newValue = this.value() + 1;
            this.valueChange.emit(newValue);
        }
    }

    decrement() {
        if (this.value() > this.minValue() && !this.disabled()) {
            const newValue = this.value() - 1;
            this.valueChange.emit(newValue);
        }
    }

    get canIncrement(): boolean {
        return !this.disabled() && this.value() < this.maxValue();
    }

    get canDecrement(): boolean {
        return !this.disabled() && this.value() > this.minValue();
    }

    onInputBlur(event: Event) {
        const target = event.target as HTMLInputElement;
        const newValue = parseInt(target.value, 10);

        if (isNaN(newValue)) {
            target.value = this.value().toString();
        } else if (newValue < this.minValue()) {
            target.value = this.minValue().toString();
            this.valueChange.emit(this.minValue());
        } else if (newValue > this.maxValue()) {
            target.value = this.maxValue().toString();
            this.valueChange.emit(this.maxValue());
        } else if (newValue !== this.value()) {
            this.valueChange.emit(newValue);
        }
    }
}
