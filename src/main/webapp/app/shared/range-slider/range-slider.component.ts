import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

const DEFAULT_STEP = 1;

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule],
})
export class RangeSliderComponent implements OnInit, OnDestroy {
    @Input() generalMaxValue: number;
    @Input() generalMinValue: number;
    @Input() step: number = DEFAULT_STEP;
    @Input() label: string;

    @Input() selectedMinValue: number;
    @Input() selectedMaxValue: number;
    @Output() selectedMinValueChange: EventEmitter<number> = new EventEmitter<number>();
    @Output() selectedMaxValueChange: EventEmitter<number> = new EventEmitter<number>();

    rangeInputElements?: NodeList;
    eventListeners: { element: HTMLInputElement; listener: (event: Event) => void }[] = [];

    get sliderMinPercentage(): number {
        return ((this.selectedMinValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    get sliderMaxPercentage(): number {
        return 100 - ((this.selectedMaxValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    ngOnInit() {
        this.rangeInputElements = document.querySelectorAll('.range-input input');

        this.rangeInputElements?.forEach((input: HTMLInputElement) => {
            const listener = (event: Event) => {
                this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
            };
            input.addEventListener('input', listener);
            this.eventListeners.push({ element: input, listener });
        });
    }

    ngOnDestroy() {
        this.eventListeners.forEach(({ element, listener }) => {
            element.removeEventListener('input', listener);
        });
    }

    onSelectedMinValueChanged(event: Event): void {
        const updatedMinValue = this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        this.selectedMinValueChange.emit(updatedMinValue);
    }

    onSelectedMaxValueChanged(event: Event): void {
        const updatedMaxValue = this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        this.selectedMaxValueChange.emit(updatedMaxValue);
    }

    private ensureMinValueIsSmallerThanMaxValueViceVersa(event: Event): number {
        const input = event.target as HTMLInputElement;
        const minSliderIsUpdated = input.className.includes('range-min');

        if (minSliderIsUpdated) {
            if (this.selectedMinValue >= this.selectedMaxValue) {
                this.selectedMinValue = this.selectedMaxValue - this.step;
            }
            return this.selectedMinValue;
        }

        if (this.selectedMaxValue <= this.selectedMinValue) {
            this.selectedMaxValue = this.selectedMinValue + this.step;
        }
        return this.selectedMaxValue;
    }
}
