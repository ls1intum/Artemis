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
    progressElement?: any;

    eventListeners: { element: any; listener: any }[] = [];

    sliderMinPercentage = '50%';
    sliderMaxPercentage = '75%';

    ngOnInit() {
        this.rangeInputElements = document.querySelectorAll('.range-input input');
        this.progressElement = document.querySelector('.slider .progress') ?? undefined;

        this.rangeInputElements?.forEach((input) => {
            const listener = (event: any) => {
                this.updateProgress(event);
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

    updateProgress(event: any) {
        if (!this.progressElement) {
            return;
        }

        const isMinMaxDifferenceLessThanStepWidth = this.maxValue - this.minValue < this.stepWidth;
        if (isMinMaxDifferenceLessThanStepWidth) {
            this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        }

        this.sliderMinPercentage = (this.minValue / this.generalMaxValue) * 100 + '%';
        this.sliderMaxPercentage = 100 - (this.maxValue / this.generalMaxValue) * 100 + '%';

        this.progressElement.style.left = this.sliderMinPercentage;
        this.progressElement.style.right = this.sliderMaxPercentage;
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
