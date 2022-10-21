import { Component, Input } from '@angular/core';
import { buildFeedbackTextForReview, Feedback } from 'app/entities/feedback.model';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faCommentDots } from '@fortawesome/free-regular-svg-icons';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-additional-feedback',
    templateUrl: './additional-feedback.component.html',
    styleUrls: ['./additional-feedback.component.scss'],
})
export class AdditionalFeedbackComponent {
    @Input()
    feedback: Feedback[];
    @Input()
    additional: boolean;
    @Input()
    course?: Course;

    // Icons
    faCommentDots = faCommentDots;
    faExclamationTriangle = faExclamationTriangle;

    // Expose the function to the template
    readonly getCourseFromExercise = getCourseFromExercise;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    constructor(private translateService: TranslateService, private localeConversionService: LocaleConversionService) {}

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
