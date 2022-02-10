import { Component, Input, OnInit, OnChanges } from '@angular/core';
import { roundScorePercentSpecifiedByCourseSettings, roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-score-display',
    templateUrl: './score-display.component.html',
    styleUrls: ['./score-display.component.scss'],
})
export class ScoreDisplayComponent implements OnInit, OnChanges {
    @Input() maxBonusPoints = 0;
    @Input() maxPoints: number;
    @Input() score: number;
    @Input() course?: Course;
    bonusPoints?: number;
    maxPointsWithBonus?: number;
    maxPercentage?: number;

    // Icons
    faQuestionCircle = faQuestionCircle;

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
                this.bonusPoints = roundScoreSpecifiedByCourseSettings(this.score - this.maxPoints, this.course);
            } else {
                this.bonusPoints = undefined;
            }
            this.maxPointsWithBonus = this.maxPoints + this.maxBonusPoints;
            this.maxPercentage = roundScorePercentSpecifiedByCourseSettings(this.maxPointsWithBonus / this.maxPoints, this.course);
        } else {
            this.bonusPoints = undefined;
            this.maxPointsWithBonus = undefined;
            this.maxPercentage = undefined;
        }
        this.score = roundScoreSpecifiedByCourseSettings(this.score, this.course);
    }
}
