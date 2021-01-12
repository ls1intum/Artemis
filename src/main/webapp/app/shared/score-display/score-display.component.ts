import { Component, Input, OnInit, OnChanges } from '@angular/core';

@Component({
    selector: 'jhi-score-display',
    templateUrl: './score-display.component.html',
    styleUrls: ['./score-display.component.scss'],
})
export class ScoreDisplayComponent implements OnInit, OnChanges {
    @Input() maxBonusPoints = 0;
    @Input() maxPoints: number;
    @Input() score: number;
    bonusPoints?: number;
    maxPointsWithBonus?: number;
    maxPercentage?: number;

    constructor() {}

    /**
     * Do nothing on initialization.
     */
    ngOnInit() {}

    /**
     * Calculate the bonus points just for display reasons
     */
    ngOnChanges() {
        if (this.maxPoints != undefined && this.maxBonusPoints > 0) {
            if (this.score > this.maxPoints) {
                this.bonusPoints = this.score - this.maxPoints;
            }
            this.maxPointsWithBonus = this.maxPoints + this.maxBonusPoints;
            this.maxPercentage = (this.maxPointsWithBonus / this.maxPoints) * 100;
        } else {
            this.bonusPoints = undefined;
            this.maxPointsWithBonus = undefined;
            this.maxPercentage = undefined;
        }
    }
}
