import { Component, Input, OnInit, OnChanges } from '@angular/core';

@Component({
    selector: 'jhi-score-display',
    templateUrl: './score-display.component.html',
    styleUrls: ['./score-display.component.scss'],
})
export class ScoreDisplayComponent implements OnInit, OnChanges {
    @Input() maxBonusPoints = 0;
    @Input() maxScore: number;
    @Input() score: number;
    bonusScore?: number | undefined;
    maxPointsWithBonus?: number | undefined;
    maxPercentage?: number | undefined;
    constructor() {}

    /**
     * Do nothing on initialization.
     */
    ngOnInit() {}

    /**
     * Calculate the bonus points just for display reasons
     */
    ngOnChanges() {
        if (this.maxScore != undefined && this.score > this.maxScore) {
            // TODO: rene: check for onlyBonus tag
            this.bonusScore = this.score - this.maxScore;
            this.maxPointsWithBonus = this.maxScore + this.maxBonusPoints;
            this.maxPercentage = (this.maxPointsWithBonus / this.maxScore) * 100;
        } else {
            this.bonusScore = undefined;
            this.maxPointsWithBonus = undefined;
            this.maxPercentage = undefined;
        }
    }
}
