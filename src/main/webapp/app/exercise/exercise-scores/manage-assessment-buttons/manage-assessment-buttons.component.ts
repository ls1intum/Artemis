import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { faBan, faClipboardList } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
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

@Component({
    selector: 'jhi-manage-assessment-buttons',
    templateUrl: './manage-assessment-buttons.component.html',
    imports: [RouterLink, FaIconComponent, ArtemisTranslatePipe],
})
export class ManageAssessmentButtonsComponent implements OnInit {
    private programmingAssessmentManualResultService = inject(ProgrammingAssessmentManualResultService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private textAssessmentService = inject(TextAssessmentService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);

    readonly exercise = input<Exercise>(undefined!);
    readonly course = input<Course>(undefined!);
    readonly participation = input.required<Participation>();
    readonly isLoading = input<boolean>(undefined!);

    readonly refresh = output<void>();

    readonly correctionRoundIndices = signal<number[]>(undefined!);
    cancelConfirmationText!: string; // resolved from the translation service in the constructor before the cancel action can be triggered
    readonly newManualResultAllowed = signal(false);
    examMode = false;

    readonly faBan = faBan;
    readonly faClipboardList = faClipboardList;
    readonly AssessmentType = AssessmentType;

    constructor() {
        const translateService = inject(TranslateService);

        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    ngOnInit(): void {
        this.newManualResultAllowed.set(areManualResultsAllowed(this.exercise()));
        this.examMode = !!this.exercise().exerciseGroup;
        if (isPracticeMode(this.participation()) && !this.examMode) {
            // don't allow manual results for practice mode participations
            this.newManualResultAllowed.set(false);
        }
        // ngFor needs an array to iterate over. This creates an array in the form of [0, 1, ...] up to the correction rounds exclusively (normally 1 or 2)
        this.correctionRoundIndices.set([...Array(this.exercise().exerciseGroup?.exam?.numberOfCorrectionRoundsInExam ?? 1).keys()]);
    }

    /**
     * The result of the given correction round, matched on the round the result belongs to.
     *
     * Indexing the results by the round only worked while a submission's results were an ordered list whose position was
     * the round. They are a set now and the round lives on the result, so it is matched instead. The scores view is fed
     * one entry per round for exactly this lookup.
     */
    resultForRound(correctionRound: number): Result | undefined {
        const submission = this.participation().submissions?.[0];
        return submission ? getSubmissionResultByCorrectionRound(submission, correctionRound) : undefined;
    }

    getAssessmentLink(correctionRound = 0) {
        const exercise = this.exercise();
        const course = this.course();
        const participation = this.participation();
        const submission = participation.submissions?.[0];
        if (!exercise.type || !exercise.id || !course.id || !submission?.id) {
            return;
        }
        correctionRound = this.getCorrectionRoundForAssessmentLink(correctionRound);

        return getLinkToSubmissionAssessment(
            exercise.type,
            course.id,
            exercise.id,
            participation.id,
            submission.id,
            exercise.exerciseGroup?.exam?.id,
            exercise.exerciseGroup?.id,
            // TODO do we need to handle this differently for programming exercises?
            this.resultForRound(correctionRound)?.id,
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
    cancelAssessment(result: Result, participation: Participation) {
        // Take the submission from the participation, not from the result. The scores overview builds its rows from
        // ParticipationScoreDTO (ExerciseScoresComponent#toParticipation), and those results carry no back reference to
        // their submission, so `result.submission?.id` was always undefined here and the guard below silently swallowed
        // every click: no request ever left the client and the lock was never released (#13396).
        const submissionId = participation.submissions?.[0]?.id;
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
