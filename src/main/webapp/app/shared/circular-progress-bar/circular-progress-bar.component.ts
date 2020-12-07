import { Component, Input, OnChanges, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-circular-progress-bar',
    templateUrl: './circular-progress-bar.component.html',
    styleUrls: ['./circular-progress-bar.component.scss'],
})
export class CircularProgressBarComponent implements OnChanges {
    @Input()
    progressInPercent = 0;
    @Input()
    progressText = 'Completed';
    circleColor = '#000000';

    constructor() {}

    ngOnChanges() {
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
        let value = Math.min(Math.max(0, this.progressInPercent / 100.0), 1) * 510;
        let redValue;
        let greenValue;
        if (value < 255) {
            redValue = 255;
            greenValue = Math.sqrt(value) * 16;
            greenValue = Math.round(greenValue);
        } else {
            greenValue = 255;
            value = value - 255;
            redValue = 255 - (value * value) / 255;
            redValue = Math.round(redValue);
        }

        return '#' + this.intToTwoDigitHex(redValue) + this.intToTwoDigitHex(greenValue) + '00';
    }
}
