import { Component, OnDestroy, OnInit } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge.component';

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule, CustomExerciseCategoryBadgeComponent],
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

        const minSliderIsUpdated = event.target.className.includes('range-min');

        console.log(event);

        console.log('minSliderIsUpdated', minSliderIsUpdated);

        if (minSliderIsUpdated) {
            if (this.minValue >= this.maxValue) {
                this.minValue = this.maxValue - this.stepWidth;
            }
        } else {
            if (this.maxValue <= this.minValue) {
                this.maxValue = this.minValue + this.stepWidth;
            }
        }

        this.progressElement.style.left = (this.minValue / this.generalMaxValue) * 100 + '%';
        this.progressElement.style.right = 100 - (this.maxValue / this.generalMaxValue) * 100 + '%';
    }
}
