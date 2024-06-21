import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule],
})
export class RangeSliderComponent implements OnInit, OnDestroy {
    @Input() selectedMinValue: number;
    @Input() selectedMaxValue: number;

    @Input() generalMaxValue: number;
    @Input() generalMinValue: number;
    @Input() stepWidth: number = 1;

    get sliderMinPercentage(): number {
        return ((this.selectedMinValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    get sliderMaxPercentage(): number {
        return 100 - ((this.selectedMaxValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    rangeInputElements?: NodeList;
    eventListeners: { element: any; listener: any }[] = [];

    ngOnInit() {
        this.rangeInputElements = document.querySelectorAll('.range-input input');

        this.rangeInputElements?.forEach((input) => {
            const listener = (event: any) => {
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

    private ensureMinValueIsSmallerThanMaxValueViceVersa(event: any) {
        const minSliderIsUpdated = event.target.className.includes('range-min');
        if (minSliderIsUpdated) {
            if (this.selectedMinValue >= this.selectedMaxValue) {
                this.selectedMinValue = this.selectedMaxValue - this.stepWidth;
            }
        } else {
            if (this.selectedMaxValue <= this.selectedMinValue) {
                this.selectedMaxValue = this.selectedMinValue + this.stepWidth;
            }
        }
    }
}
