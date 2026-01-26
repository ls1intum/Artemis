import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { faBan, faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { areManualResultsAllowed } from 'app/exercise/util/exercise.utils';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { getLinkToSubmissionAssessment } from 'app/shared/util/navigation.utils';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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

    @Input() exercise: Exercise;
    @Input() course: Course;
    @Input() participation: Participation;
    @Input() isLoading: boolean;

    @Output() refresh = new EventEmitter<void>();

    correctionRoundIndices: number[];
    cancelConfirmationText: string;
    newManualResultAllowed = false;
    examMode = false;

    readonly faBan = faBan;
    readonly faFolderOpen = faFolderOpen;
    readonly AssessmentType = AssessmentType;

    constructor() {
        const translateService = inject(TranslateService);

        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    ngOnInit(): void {
        this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
        this.examMode = !!this.exercise.exerciseGroup;
        if (isPracticeMode(this.participation) && !this.examMode) {
            // don't allow manual results for practice mode participations
            this.newManualResultAllowed = false;
        }
        // ngFor needs an array to iterate over. This creates an array in the form of [0, 1, ...] up to the correction rounds exclusively (normally 1 or 2)
        this.correctionRoundIndices = [...Array(this.exercise.exerciseGroup?.exam?.numberOfCorrectionRoundsInExam ?? 1).keys()];
    }

    getAssessmentLink(correctionRound = 0) {
        if (!this.exercise.type || !this.exercise.id || !this.course.id || !this.participation.submissions?.[0]?.id) {
            return;
        }
        correctionRound = this.getCorrectionRoundForAssessmentLink(correctionRound);

        return getLinkToSubmissionAssessment(
            this.exercise.type,
            this.course.id,
            this.exercise.id,
            this.participation.id,
            this.participation.submissions?.[0]?.id,
            this.exercise.exerciseGroup?.exam?.id,
            this.exercise.exerciseGroup?.id,
            // TODO do we need to handle this differently for programming exercises?
            this.participation.submissions[0].results?.[correctionRound]?.id,
        );
    }

    getCorrectionRoundForAssessmentLink(correctionRound = 0): number {
        // TODO do we need to handle this differently for programming exercises?
        const result = this.participation.submissions![0].results?.[correctionRound];
        if (!result) {
            return correctionRound;
        }
        if (result.hasComplaint && !!this.participation.submissions![0].results?.[correctionRound + 1]) {
            // If there is a complaint and the complaint got accepted (additional result)
            // open this next result.
            return correctionRound + 1;
        }
        return correctionRound;
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(result: Result, participation: Participation) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);

        if (confirmCancel && result.submission?.id) {
            let cancelSubscription;
            switch (this.exercise.type) {
                case ExerciseType.PROGRAMMING:
                    cancelSubscription = this.programmingAssessmentManualResultService.cancelAssessment(result.submission.id);
                    break;
                case ExerciseType.MODELING:
                    cancelSubscription = this.modelingAssessmentService.cancelAssessment(result.submission.id);
                    break;
                case ExerciseType.TEXT:
                    cancelSubscription = this.textAssessmentService.cancelAssessment(participation.id!, result.submission.id);
                    break;
                case ExerciseType.FILE_UPLOAD:
                    cancelSubscription = this.fileUploadAssessmentService.cancelAssessment(result.submission.id);
                    break;
            }
            cancelSubscription?.subscribe(() => {
                // TODO: The 'emit' function requires a mandatory void argument
                this.refresh.emit();
            });
        }
    }
}
