import { Component, EventEmitter, HostBinding, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Subscription, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';

import { AssessmentHeaderComponent } from '../assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from '../assessment-complaint-alert/assessment-complaint-alert.component';
import { AssessmentNoteComponent } from '../assessment-note/assessment-note.component';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';

import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';

/**
 * The <jhi-assessment-layout> component provides the basic layout for an assessment page.
 * It shows the header, alerts for complaints on top and the complaint form at the bottom of the page.
 * The actual assessment needs to be inserted using content projection.
 * Components using this component need to provide Inputs and handle Outputs. This component does not perform assessment logic.
 */
@Component({
    selector: 'jhi-assessment-layout',
    templateUrl: './assessment-layout.component.html',
    styleUrls: ['./assessment-layout.component.scss'],
    imports: [AssessmentHeaderComponent, AssessmentComplaintAlertComponent, AssessmentNoteComponent, ComplaintsForTutorComponent],
})
export class AssessmentLayoutComponent implements OnChanges, OnDestroy {
    @HostBinding('class.assessment-container') readonly assessmentContainerClass = true;
    private exerciseService = inject(ExerciseService);

    /** Subscription to the stats endpoint so we can clean it up on destroy */
    private statsSub?: Subscription;

    @Output() navigateBack = new EventEmitter<void>();
    MORE_FEEDBACK = ComplaintType.MORE_FEEDBACK;

    @Input() isLoading: boolean;
    @Input() saveBusy: boolean;
    @Input() submitBusy: boolean;
    @Input() cancelBusy: boolean;
    @Input() nextSubmissionBusy: boolean;
    @Input() correctionRound: number;

    @Input() isTeamMode: boolean;
    @Input() isAssessor: boolean;
    @Input() canOverride: boolean;
    @Input() isTestRun = false;
    @Input() exerciseDashboardLink: string[];

    @Input() result?: Result;
    @Input() assessmentsAreValid: boolean;
    @Input() complaint?: Complaint;
    @Input() exercise?: Exercise;
    @Input() submission?: Submission;

    /**
     * Flag: whether there are unassessed submissions left
     * for the current exercise **in the current correction round**.
     *
     * This is dynamically refreshed from the backend stats endpoint.
     * Bound into <jhi-assessment-header> and used to hide/show
     * the “Assess Next” button and internal notes section.
     */
    @Input() hasUnassessedSubmissions = true;

    @Input() hasAssessmentDueDatePassed: boolean;
    @Input() isProgrammingExercise: boolean; // remove once diff view activated for programming exercises

    private _highlightDifferences: boolean;

    @Input() set highlightDifferences(highlightDifferences: boolean) {
        this._highlightDifferences = highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }
    get highlightDifferences() {
        return this._highlightDifferences;
    }

    setAssessmentNoteForResult(assessmentNote: AssessmentNote) {
        if (this.result) {
            this.result.assessmentNote = assessmentNote;
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        // Refresh stats when exercise/result/correctionRound changes
        if (changes['exercise'] || changes['result'] || changes['correctionRound']) {
            this.refreshHasUnassessedSubmissions();
        }
    }

    ngOnDestroy() {
        // Clean up subscription to avoid memory leaks
        this.statsSub?.unsubscribe();
    }

    /**
     * Calls the backend tutor stats endpoint for this exercise.
     * Computes the number of remaining unassessed submissions
     * = submitted - assessed - locked,
     * where “assessed” is taken from the stats of the
     * **currently active correction round** (if available).
     */
    private refreshHasUnassessedSubmissions() {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            this.hasUnassessedSubmissions = false;
            this.statsSub?.unsubscribe();
            return;
        }

        this.statsSub?.unsubscribe();
        this.statsSub = this.exerciseService
            .getStatsForTutors(exerciseId)
            .pipe(
                map((r: HttpResponse<StatsForDashboard>) => r.body ?? null),
                map((stats: StatsForDashboard | null) => {
                    if (!stats) return false;

                    // Ignore `total`, sum parts with 0-defaults; handle null/undefined
                    const toNum = (x?: { inTime?: number; late?: number; total?: number } | null) => (x?.inTime ?? 0) + (x?.late ?? 0);

                    const submitted = toNum(stats.numberOfSubmissions);
                    const rounds = stats.numberOfAssessmentsOfCorrectionRounds as Array<{ inTime?: number; late?: number; total?: number }> | undefined;

                    // Safeguard: capture `correctionRound`, validate before indexing (no non-null assertion)

                    const round = this.correctionRound;
                    let assessed: number;
                    if (Array.isArray(rounds) && Number.isInteger(round) && round >= 0 && round < rounds.length && rounds[round] != null) {
                        assessed = toNum(rounds[round]);
                    } else {
                        assessed = toNum(stats.totalNumberOfAssessments);
                    }

                    const locked = stats.totalNumberOfAssessmentLocks ?? 0;
                    const remaining = Math.max(0, submitted - assessed - locked);

                    return remaining > 0;
                }),
                catchError(() => of(false)),
            )
            .subscribe((v: boolean) => (this.hasUnassessedSubmissions = v));
    }

    @Output() save = new EventEmitter<void>();
    @Output() onSubmit = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() updateAssessmentAfterComplaint = new EventEmitter<AssessmentAfterComplaint>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();
    @Output() useAsExampleSubmission = new EventEmitter<void>();
}
