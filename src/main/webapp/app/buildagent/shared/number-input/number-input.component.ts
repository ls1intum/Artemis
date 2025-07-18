import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-number-input',
    templateUrl: './number-input.component.html',
    styleUrl: './number-input.component.scss',
    imports: [FontAwesomeModule, CommonModule],
})
export class NumberInputComponent {
    @Input({ required: true }) value!: number;
    @Input() minValue: number = Number.MIN_SAFE_INTEGER;
    @Input() maxValue: number = Number.MAX_SAFE_INTEGER;
    @Input() disabled: boolean = false;
    @Output() valueChange = new EventEmitter<number>();

    // Icons
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;

    increment() {
        if (this.value < this.maxValue && !this.disabled) {
            const newValue = this.value + 1;
            this.valueChange.emit(newValue);
        }
    }

    decrement() {
        if (this.value > this.minValue && !this.disabled) {
            const newValue = this.value - 1;
            this.valueChange.emit(newValue);
        }
    }

    get canIncrement(): boolean {
        return !this.disabled && this.value < this.maxValue;
    }

    get canDecrement(): boolean {
        return !this.disabled && this.value > this.minValue;
    }

    onInputBlur(event: Event) {
        const target = event.target as HTMLInputElement;
        const newValue = parseInt(target.value, 10);

        if (isNaN(newValue)) {
            target.value = this.value.toString();
        } else if (newValue < this.minValue) {
            target.value = this.minValue.toString();
            this.valueChange.emit(this.minValue);
        } else if (newValue > this.maxValue) {
            target.value = this.maxValue.toString();
            this.valueChange.emit(this.maxValue);
        } else if (newValue !== this.value) {
            this.valueChange.emit(newValue);
        }
    }
}
