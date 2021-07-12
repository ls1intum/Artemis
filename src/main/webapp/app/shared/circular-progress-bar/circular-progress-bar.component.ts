import { Component, Input, OnChanges } from '@angular/core';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-circular-progress-bar',
    templateUrl: './circular-progress-bar.component.html',
    styleUrls: ['./circular-progress-bar.component.scss'],
})
export class CircularProgressBarComponent implements OnChanges {
    @Input()
    progressInPercent = 0;
    progressUsedForColorCalculation = 0;

    @Input()
    progressText = 'Completed';
    circleColor = '#000000';

    constructor() {}

    ngOnChanges() {
        if (this.progressInPercent > 100) {
            this.progressUsedForColorCalculation = 100;
        }
        if (this.progressInPercent < 0) {
            this.progressUsedForColorCalculation = 0;
        }
        // rounded to nearest integer
        this.progressUsedForColorCalculation = round(this.progressInPercent);
        this.circleColor = this.calculateCircleColor();
    }

    intToTwoDigitHex(i: any) {
        const hex = parseInt(i, undefined).toString(16);
        return hex.length < 2 ? '0' + hex : hex;
    }

    /**
     * Provides a smooth transition from red, yellow to finally green depending on the progress bar percentage
     */
    calculateCircleColor() {
        let value = Math.min(Math.max(0, this.progressUsedForColorCalculation / 100.0), 1) * 510;
        let redValue;
        let greenValue;
        if (value < 255) {
            redValue = 255;
            greenValue = Math.sqrt(value) * 16;
            greenValue = round(greenValue);
        } else {
            greenValue = 255;
            value = value - 255;
            redValue = 255 - (value * value) / 255;
            redValue = round(redValue);
        }

        return '#' + this.intToTwoDigitHex(redValue) + this.intToTwoDigitHex(greenValue) + '00';
    }
}
