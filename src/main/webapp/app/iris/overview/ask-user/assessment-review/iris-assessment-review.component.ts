import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { faCheck, faX } from '@fortawesome/free-solid-svg-icons';
import { map, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { Course } from 'app/course/shared/entities/course.model';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisAssessmentReviewResolvedData } from 'app/iris/overview/ask-user/services/iris-assessment-review-resolver.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';

@Component({
    selector: 'jhi-iris-assessment-review',
    templateUrl: './iris-assessment-review.component.html',
    styleUrl: './iris-assessment-review.component.scss',
    imports: [TranslateDirective, ArtemisTranslatePipe, TumUiButtonComponent, TumUiTableDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Displays the Iris ask-user-mode assessment for a single participation and lets an
 * instructor accept or reject its verdict.
 */
export class IrisAssessmentReviewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly irisAssessmentReviewService = inject(IrisAssessmentReviewHttpService);
    private readonly alertService = inject(AlertService);

    private readonly resolvedData = toSignal(this.route.data.pipe(map((data) => data['reviewData'] as IrisAssessmentReviewResolvedData)), { requireSync: true });

    protected readonly faCheck = faCheck;
    protected readonly faX = faX;

    protected readonly IrisVerdictReview = IrisVerdictReview;

    protected readonly course = computed<Course>(() => this.resolvedData().course);

    protected readonly exercise = computed<ProgrammingExercise>(() => this.resolvedData().exercise);

    protected readonly rows = computed<QAExchangeDTO[]>(() => this.resolvedData().rows);

    /**
     * The initially resolved assessment can also be updated locally when the
     * instructor accepts or rejects it.
     *
     * linkedSignal resets the local value if the resolved route data changes.
     */
    protected readonly assessment = linkedSignal<IrisAssessment>(() => this.resolvedData().assessment);

    /**
     * Translation key suffix for the assessment's verdict (suspicious/unsuspicious).
     */
    protected readonly verdictTranslationSuffix = computed(() => {
        switch (this.assessment().verdict) {
            case IrisVerdict.SUSPICIOUS:
                return 'suspicious';
            case IrisVerdict.UNSUSPICIOUS:
                return 'unsuspicious';
            default:
                return undefined;
        }
    });

    /**
     * Translation key suffix for the instructor's review decision (accepted/rejected).
     */
    protected readonly reviewTranslationSuffix = computed(() => {
        switch (this.assessment().verdictReview) {
            case IrisVerdictReview.ACCEPTED:
                return 'accepted';
            case IrisVerdictReview.REJECTED:
                return 'rejected';
            default:
                return undefined;
        }
    });

    protected acceptAnswers(): void {
        this.updateReview(IrisVerdictReview.ACCEPTED);
    }

    protected rejectAnswers(): void {
        this.updateReview(IrisVerdictReview.REJECTED);
    }

    /**
     * Applies the given review decision optimistically, then persists it to the server.
     * Reverts the local state if the request fails.
     * @param verdictReview The review decision to apply (accepted or rejected)
     */
    private updateReview(verdictReview: IrisVerdictReview): void {
        const currentAssessment = this.assessment();

        if (currentAssessment.id === undefined || currentAssessment.verdictReview === verdictReview) {
            return;
        }

        const previousAssessment = currentAssessment;

        const updatedAssessment = {
            ...currentAssessment,
            verdictReview,
        };

        // Immutable update: the signal receives a new object reference.
        this.assessment.set(updatedAssessment);

        const request$ =
            verdictReview === IrisVerdictReview.ACCEPTED
                ? this.irisAssessmentReviewService.acceptAnswers(currentAssessment.id)
                : this.irisAssessmentReviewService.rejectAnswers(currentAssessment.id);

        request$.pipe(take(1)).subscribe({
            error: (error: HttpErrorResponse) => {
                // Restore the previous state if the server request fails.
                this.assessment.set(previousAssessment);
                onError(this.alertService, error);
            },
        });
    }
}
