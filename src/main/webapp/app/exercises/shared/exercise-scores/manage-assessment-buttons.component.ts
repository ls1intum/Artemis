import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faBan, faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise.utils';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';

@Component({
    selector: 'jhi-manage-assessment-buttons',
    templateUrl: './manage-assessment-buttons.component.html',
})
export class ManageAssessmentButtonsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() course: Course;
    @Input() participation: Participation;
    @Input() correctionRoundIndices: number[];
    @Input() isLoading: boolean;

    @Output() refresh = new EventEmitter<void>();

    cancelConfirmationText: string;
    newManualResultAllowed: boolean = false;

    readonly faBan = faBan;
    readonly faFolderOpen = faFolderOpen;
    readonly AssessmentType = AssessmentType;

    constructor(
        translateService: TranslateService,
        private programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService,
        private modelingAssessmentService: ModelingAssessmentService,
        private textAssessmentService: TextAssessmentService,
        private fileUploadAssessmentService: FileUploadAssessmentService,
    ) {
        translateService.get('artemisApp.programmingAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    ngOnInit(): void {
        this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
    }

    getAssessmentLink(participation: Participation, correctionRound = 0) {
        if (!this.exercise.type || !this.exercise.id || !this.course.id || !participation.submissions?.[0]?.id) {
            return;
        }
        correctionRound = this.getCorrectionRoundForAssessmentLink(participation, correctionRound);

        return getLinkToSubmissionAssessment(
            this.exercise.type,
            this.course.id,
            this.exercise.id,
            participation.id,
            participation.submissions?.[0]?.id,
            this.exercise.exerciseGroup?.exam?.id,
            this.exercise.exerciseGroup?.id,
            participation.results?.[correctionRound]?.id,
        );
    }

    getCorrectionRoundForAssessmentLink(participation: Participation, correctionRound = 0): number {
        const result = participation.results?.[correctionRound];
        if (!result) {
            return correctionRound;
        }
        if (result.hasComplaint && !!participation.results?.[correctionRound + 1]) {
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
                this.refresh.emit();
            });
        }
    }
}
