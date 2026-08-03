import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faBrain } from '@fortawesome/free-solid-svg-icons';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { ButtonModule } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-iris-review-assessment-button',
    templateUrl: './iris-review-assessment-button.component.html',
    imports: [FeatureToggleDirective, ArtemisTranslatePipe, RouterLink, ButtonModule, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisReviewAssessmentButtonComponent {
    readonly course = input.required<Course>();
    readonly exercise = input.required<ProgrammingExercise>();
    readonly participation = input.required<ProgrammingExerciseStudentParticipation>();
    readonly smallButton = input.required<boolean>();
    readonly hideLabelMobile = input(false);
    readonly inClass = input(false);

    protected readonly faBrain = faBrain;
    protected readonly FeatureToggle = FeatureToggle;

    protected readonly irisAssessment = computed(() => this.participation().irisAssessment);
    protected readonly assessmentReviewPath = computed(() => (this.inClass() ? 'iris-in-class-assessments' : 'iris-assessments'));

    protected readonly verdictReview = computed(() => this.irisAssessment()?.verdictReview);
    protected readonly verdict = computed(() => this.irisAssessment()?.verdict);
    protected readonly buttonSize = computed<'small' | undefined>(() => (this.smallButton() ? 'small' : undefined));

    // Returns true when the assessment is either suspicious but not yet reviewed, reviewed as rejected oder the quiz has not been done yet (verdict is missing)
    protected readonly needsAttention = computed(() => {
        const verdictReview = this.verdictReview();
        const verdict = this.verdict();

        return (
            ((verdictReview === undefined || verdictReview === null) && verdict === IrisVerdict.SUSPICIOUS) ||
            verdictReview === IrisVerdictReview.REJECTED ||
            verdict === undefined ||
            verdict === null
        );
    });
    protected readonly buttonSeverity = computed<'danger' | 'success'>(() => (this.needsAttention() ? 'danger' : 'success'));

    protected readonly label = computed(() => {
        const labelPrefix = 'artemisApp.exerciseActions.reviewIrisAssessment.';

        switch (this.verdictReview()) {
            case IrisVerdictReview.REJECTED:
                return `${labelPrefix}rejected`;
            case IrisVerdictReview.ACCEPTED:
                return `${labelPrefix}accepted`;
            default:
                switch (this.verdict()) {
                    case IrisVerdict.UNSUSPICIOUS:
                        return `${labelPrefix}unsuspicious`;
                    case IrisVerdict.SUSPICIOUS:
                        return `${labelPrefix}suspicious`;
                    default:
                        return `${labelPrefix}missing`;
                }
        }
    });
    protected readonly IrisPipeEvent = IrisPipeEvent;
}
