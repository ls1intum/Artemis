import { Component, computed, inject, input, output } from '@angular/core';
import { faBan, faClipboardList } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { AssessmentDraftDTO } from 'app/exercise/participation/assessment-queue-dto.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getSubmissionResultByCorrectionRound } from 'app/exercise/shared/entities/submission/submission.model';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { areManualResultsAllowed } from 'app/exercise/util/exercise.utils';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiButtonDirective, TumAetUiButtonSeverity, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

type AssessmentLink = {
    correctionRound: number;
    result: Result | undefined;
    action: 'assess' | 'open' | 'continue' | 'complaint';
    severity: TumAetUiButtonSeverity;
    labelKey: string;
    link: string[] | undefined;
    targetRound: number;
    submissionId: number | undefined;
    canCancel: boolean;
};

@Component({
    selector: 'jhi-manage-assessment-buttons',
    templateUrl: './manage-assessment-buttons.component.html',
    imports: [RouterLink, FaIconComponent, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiTooltipDirective],
})
export class ManageAssessmentButtonsComponent {
    private programmingAssessmentManualResultService = inject(ProgrammingAssessmentManualResultService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private textAssessmentService = inject(TextAssessmentService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);

    readonly exercise = input<Exercise>(undefined!);
    readonly course = input<Course>(undefined!);
    readonly participation = input.required<Participation>();
    readonly isLoading = input<boolean>(undefined!);

    readonly refresh = output<void>();

    readonly disabled = input(false);
    readonly disabledTooltip = input('');
    readonly correctionRoundsEnabled = input<number>();
    readonly correctionRound = input<number>();
    readonly assessableCorrectionRounds = input<number[]>();
    readonly ownedCorrectionRounds = input<number[]>();
    readonly isTestRun = input(false);
    readonly olderAssessmentDrafts = input<AssessmentDraftDTO[]>([]);
    readonly examMode = computed(() => !!this.exercise().exerciseGroup);
    readonly newManualResultAllowed = computed(() => areManualResultsAllowed(this.exercise()) && (!isPracticeMode(this.participation()) || this.examMode()));
    readonly correctionRoundIndices = computed(() =>
        this.correctionRound() === undefined
            ? [...Array(this.correctionRoundsEnabled() ?? this.exercise().exerciseGroup?.exam?.numberOfCorrectionRoundsInExam ?? 1).keys()]
            : [this.correctionRound()!],
    );
    readonly assessmentLinks = computed<AssessmentLink[]>(() => [
        ...this.correctionRoundIndices().flatMap((correctionRound) => {
            const result = this.resultForRound(correctionRound);
            const action = this.assessmentAction(correctionRound);
            const targetRound = this.getCorrectionRoundForAssessmentLink(correctionRound);
            const canManageDraft = this.exercise().isAtLeastInstructor || this.ownedCorrectionRounds() === undefined || this.ownedCorrectionRounds()!.includes(correctionRound);
            if (
                !this.participation().submissions?.[0]?.id ||
                (action === 'assess' && correctionRound > 0 && this.assessableCorrectionRounds() === undefined && !this.resultForRound(correctionRound - 1)?.completionDate) ||
                (!this.newManualResultAllowed() &&
                    (!result?.assessmentType || result.assessmentType === AssessmentType.AUTOMATIC || result.assessmentType === AssessmentType.AUTOMATIC_ATHENA)) ||
                ((action === 'continue' || (this.examMode() && (action === 'open' || action === 'complaint'))) && !canManageDraft) ||
                (action === 'assess' && this.assessableCorrectionRounds() !== undefined && !this.assessableCorrectionRounds()!.includes(correctionRound))
            ) {
                return [];
            }
            return [
                {
                    correctionRound,
                    result,
                    action,
                    severity: action === 'assess' ? 'success' : action === 'continue' ? 'warn' : 'primary',
                    labelKey:
                        action === 'complaint'
                            ? 'artemisApp.exerciseAssessmentDashboard.showComplaint'
                            : 'artemisApp.assessment.dashboard.actions.' + (this.examMode() ? 'examCorrectionRound.' : '') + action,
                    link: this.getAssessmentLink(targetRound),
                    targetRound,
                    submissionId: this.participation().submissions?.[0]?.id,
                    canCancel:
                        canManageDraft && this.newManualResultAllowed() && !!result?.assessmentType && !result.completionDate && result.assessmentType !== AssessmentType.AUTOMATIC,
                } satisfies AssessmentLink,
            ];
        }),
        ...this.olderAssessmentDrafts().map((draft): AssessmentLink => {
            const result = new Result();
            result.id = draft.resultId;
            result.assessmentType = AssessmentType.MANUAL;
            const correctionRound = draft.correctionRound ?? 0;
            return {
                correctionRound,
                result,
                action: 'continue',
                severity: 'warn',
                labelKey: 'artemisApp.assessment.dashboard.actions.' + (this.examMode() ? 'examCorrectionRound.' : '') + 'continue',
                link: this.getAssessmentLink(correctionRound, draft.submissionId, draft.resultId),
                targetRound: correctionRound,
                submissionId: draft.submissionId,
                canCancel: this.newManualResultAllowed(),
            };
        }),
    ]);
    cancelConfirmationText!: string;

    readonly faBan = faBan;
    readonly faClipboardList = faClipboardList;
    readonly AssessmentType = AssessmentType;

    constructor() {
        const translateService = inject(TranslateService);

        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * The result of the given correction round, matched on the round the result belongs to.
     *
     * Indexing the results by the round only worked while a submission's results were an ordered list whose position was
     * the round. They are a set now and the round lives on the result, so it is matched instead. The participation page is fed
     * one entry per round for exactly this lookup.
     */
    resultForRound(correctionRound: number): Result | undefined {
        const submission = this.participation().submissions?.[0];
        return submission ? getSubmissionResultByCorrectionRound(submission, correctionRound) : undefined;
    }

    assessmentAction(correctionRound: number): 'assess' | 'open' | 'continue' | 'complaint' {
        const result = this.resultForRound(correctionRound);
        if (result?.hasComplaint) {
            return 'complaint';
        }
        return !result?.assessmentType || result.assessmentType === AssessmentType.AUTOMATIC ? 'assess' : result.completionDate ? 'open' : 'continue';
    }

    getAssessmentLink(correctionRound = 0, submissionId?: number, resultId?: number) {
        const exercise = this.exercise();
        const course = this.course();
        const participation = this.participation();
        const currentSubmission = participation.submissions?.[0];
        submissionId ??= currentSubmission?.id;
        if (!exercise.type || !exercise.id || !course.id || !submissionId) {
            return;
        }
        return getLinkToSubmissionAssessment(
            exercise.type,
            course.id,
            exercise.id,
            participation.id,
            submissionId,
            exercise.exerciseGroup?.exam?.id,
            exercise.exerciseGroup?.id,
            // TODO do we need to handle this differently for programming exercises?
            exercise.isAtLeastInstructor ? (resultId ?? this.resultForRound(correctionRound)?.id) : undefined,
        );
    }

    getCorrectionRoundForAssessmentLink(correctionRound = 0): number {
        // TODO do we need to handle this differently for programming exercises?
        const result = this.resultForRound(correctionRound);
        if (!result) {
            return correctionRound;
        }
        if (result.hasComplaint && !!this.resultForRound(correctionRound + 1)) {
            // If there is a complaint and the complaint got accepted (additional result)
            // open this next result.
            return correctionRound + 1;
        }
        return correctionRound;
    }

    /**
     * Cancels the assessment the clicked button belongs to and reloads the submissions to reflect the change. The result is passed on explicitly: a submission holds one
     * result per correction round, and without it the server released the newest round, so cancelling correction round 1
     * released round 2 and round 1 stayed locked (#13396).
     */
    cancelAssessment(result: Result, participation: Participation, submissionId?: number) {
        // Take the submission from the participation, not from the result. The participation page builds its rows from
        // ParticipationManagementDTO (ParticipationComponent#toParticipation), and those results carry no back reference to
        // their submission, so `result.submission?.id` was always undefined here and the guard below silently swallowed
        // every click: no request ever left the client and the lock was never released (#13396).
        submissionId ??= participation.submissions?.[0]?.id;
        const confirmCancel = window.confirm(this.cancelConfirmationText);

        if (confirmCancel && submissionId) {
            let cancelSubscription;
            switch (this.exercise().type) {
                case ExerciseType.PROGRAMMING:
                    cancelSubscription = this.programmingAssessmentManualResultService.cancelAssessment(submissionId, result?.id);
                    break;
                case ExerciseType.MODELING:
                    cancelSubscription = this.modelingAssessmentService.cancelAssessment(submissionId, result?.id);
                    break;
                case ExerciseType.TEXT:
                    cancelSubscription = this.textAssessmentService.cancelAssessment(participation.id!, submissionId, result?.id);
                    break;
                case ExerciseType.FILE_UPLOAD:
                    cancelSubscription = this.fileUploadAssessmentService.cancelAssessment(submissionId, result?.id);
                    break;
            }
            cancelSubscription?.subscribe(() => {
                // TODO: The 'emit' function requires a mandatory void argument
                this.refresh.emit();
            });
        }
    }
}
