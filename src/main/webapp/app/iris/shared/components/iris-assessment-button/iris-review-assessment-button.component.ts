import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../../../../../../test/javascript/spec/helpers/mocks/directive/mock-router-link.directive';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { Course } from 'app/course/shared/entities/course.model';
import { IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-iris-review-assessment-button',
    templateUrl: './iris-review-assessment-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe, MockRouterLinkDirective, CommonModule],
})
export class IrisReviewAssessmentButtonComponent implements OnInit {
    @Input()
    course: Course;
    @Input()
    participation: ProgrammingExerciseStudentParticipation;
    @Input()
    smallButtons: boolean;
    @Input()
    hideLabelMobile = false;

    needsAttention = false;

    readonly faBrain = faBrain;

    ngOnInit() {
        this.needsAttention = this.participation.irisVerdictReview === IrisVerdictReview.NEEDS_REVIEW || this.participation.irisVerdictReview === undefined;
    }

    getLabel(): string {
        const label = 'artemisApp.exerciseActions.reviewIrisAssessment.';

        switch (this.participation.irisVerdictReview) {
            case IrisVerdictReview.REVIEWABLE:
                return label + 'reviewable';
            case IrisVerdictReview.NEEDS_REVIEW:
                return label + 'needsReview';
            case IrisVerdictReview.REJECTED:
            case IrisVerdictReview.ACCEPTED:
                return label + 'reviewed';
            default:
                return label + 'missing';
        }
    }

    protected readonly FeatureToggle = FeatureToggle;
}
