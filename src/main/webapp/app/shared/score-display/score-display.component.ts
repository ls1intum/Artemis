import { Component, Input, OnChanges } from '@angular/core';
import { roundScorePercentSpecifiedByCourseSettings, roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-score-display',
    templateUrl: './score-display.component.html',
    styleUrls: ['./score-display.component.scss'],
    imports: [TranslateDirective, FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class ScoreDisplayComponent implements OnChanges {
    @Input() maxBonusPoints = 0;
    @Input() maxPoints: number;
    @Input() score: number;
    @Input() course?: Course;
    bonusPoints?: number;
    maxPointsWithBonus?: number;
    maxPercentage?: number;

    // Icons
    faQuestionCircle = faQuestionCircle;

    /**
     * Calculate the bonus points just for display reasons
     */
    ngOnChanges() {
        if (this.maxPoints != undefined && this.maxBonusPoints > 0) {
            if (this.score > this.maxPoints) {
                this.bonusPoints = roundValueSpecifiedByCourseSettings(this.score - this.maxPoints, this.course);
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
        this.score = roundValueSpecifiedByCourseSettings(this.score, this.course);
    }
}
