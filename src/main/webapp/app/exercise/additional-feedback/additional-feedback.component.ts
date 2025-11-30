import { Component, Input, inject } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faCommentDots } from '@fortawesome/free-regular-svg-icons';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-additional-feedback',
    templateUrl: './additional-feedback.component.html',
    styleUrls: ['./additional-feedback.component.scss'],
    imports: [FaIconComponent, TranslateDirective, NgbTooltip, ArtemisTranslatePipe],
})
export class AdditionalFeedbackComponent {
    private translateService = inject(TranslateService);
    private localeConversionService = inject(LocaleConversionService);

    @Input() feedback: Feedback[];
    @Input() additional: boolean;
    @Input() course?: Course;

    // Icons
    faCommentDots = faCommentDots;
    faExclamationTriangle = faExclamationTriangle;

    // Expose the function to the template
    readonly getCourseFromExercise = getCourseFromExercise;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    /**
     * Translates the points string based on the singularity of the given points.
     * In addition, the points are returned in a localized form.
     * @param points Number of points assigned to a feedback
     */
    public pointTranslation(points: number): string {
        const singular = Math.abs(points) === 1;
        return this.translateService.instant(`artemisApp.assessment.detail.points.${singular ? 'one' : 'many'}`, {
            points: this.localeConversionService.toLocaleString(points, this.course?.accuracyOfScores),
        });
    }
}
