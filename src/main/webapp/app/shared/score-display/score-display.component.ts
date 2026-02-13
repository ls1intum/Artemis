import { Component, effect, input, signal } from '@angular/core';
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
export class ScoreDisplayComponent {
    maxBonusPoints = input(0);
    maxPoints = input<number>();
    score = input<number>();
    course = input<Course | undefined>(undefined);

    bonusPoints = signal<number | undefined>(undefined);
    maxPointsWithBonus = signal<number | undefined>(undefined);
    maxPercentage = signal<number | undefined>(undefined);
    roundedScore = signal<number | undefined>(undefined);

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor() {
        effect(() => {
            const maxPts = this.maxPoints();
            const maxBonusPts = this.maxBonusPoints();
            const currentScore = this.score();
            const currentCourse = this.course();

            if (maxPts != undefined && maxPts > 0 && maxBonusPts > 0) {
                if (currentScore !== undefined && currentScore > maxPts) {
                    this.bonusPoints.set(roundValueSpecifiedByCourseSettings(currentScore - maxPts, currentCourse));
                } else {
                    this.bonusPoints.set(undefined);
                }
                this.maxPointsWithBonus.set(maxPts + maxBonusPts);
                this.maxPercentage.set(roundScorePercentSpecifiedByCourseSettings((maxPts + maxBonusPts) / maxPts, currentCourse));
            } else {
                this.bonusPoints.set(undefined);
                this.maxPointsWithBonus.set(undefined);
                this.maxPercentage.set(undefined);
            }
            this.roundedScore.set(currentScore !== undefined ? roundValueSpecifiedByCourseSettings(currentScore, currentCourse) : undefined);
        });
    }
}
