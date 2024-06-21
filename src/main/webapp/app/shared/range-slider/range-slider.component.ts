import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule],
})
export class RangeSliderComponent implements OnInit, OnDestroy {
    generalMaxValue = 100;
    generalMinValue = 0;
    stepWidth = 10;

    minValue = 50;
    maxValue = 75;

    rangeInputElements?: NodeList;

    eventListeners: { element: any; listener: any }[] = [];

    sliderMinPercentage = '0%';
    sliderMaxPercentage = '100%';

    ngOnInit() {
        this.rangeInputElements = document.querySelectorAll('.range-input input');

        this.rangeInputElements?.forEach((input) => {
            const listener = (event: any) => {
                this.updateProgress(event);
            };
            input.addEventListener('input', listener);
            this.eventListeners.push({ element: input, listener });
        });

        this.updateSliderPercentage();
    }

    ngOnDestroy() {
        this.eventListeners.forEach(({ element, listener }) => {
            element.removeEventListener('input', listener);
        });
    }

    updateProgress(event: any) {
        const isMinMaxDifferenceLessThanStepWidth = this.maxValue - this.minValue < this.stepWidth;
        if (isMinMaxDifferenceLessThanStepWidth) {
            this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        }

        this.updateSliderPercentage();
    }

    private updateSliderPercentage() {
        this.sliderMinPercentage = (this.minValue / this.generalMaxValue) * 100 + '%';
        this.sliderMaxPercentage = 100 - (this.maxValue / this.generalMaxValue) * 100 + '%';
    }

    private ensureMinValueIsSmallerThanMaxValueViceVersa(event: any) {
        const minSliderIsUpdated = event.target.className.includes('range-min');
        if (minSliderIsUpdated) {
            if (this.minValue >= this.maxValue) {
                this.minValue = this.maxValue - this.stepWidth;
            }
        } else {
            if (this.maxValue <= this.minValue) {
                this.maxValue = this.minValue + this.stepWidth;
            }
        }
    }
}
