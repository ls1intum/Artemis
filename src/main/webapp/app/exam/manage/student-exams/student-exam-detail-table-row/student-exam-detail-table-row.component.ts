import { Component, Input, OnChanges } from '@angular/core';
import { ExerciseType, IncludedInOverallScore, getIcon } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { Course } from 'app/entities/course.model';
import { Result } from 'app/entities/result.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';

@Component({
    /* eslint-disable-next-line  @angular-eslint/component-selector */
    selector: '[jhi-student-exam-detail-table-row]',
    templateUrl: './student-exam-detail-table-row.component.html',
    providers: [],
})
export class StudentExamDetailTableRowComponent implements OnChanges {
    @Input() examId: number;
    @Input() isTestRun: boolean;
    @Input() course: Course;
    @Input() busy: boolean;
    @Input() studentExam: StudentExam;
    @Input() exerciseId?: number;
    @Input() exerciseType?: ExerciseType;
    @Input() result?: Result;
    @Input() submission?: Submission;
    @Input() studentParticipationId?: number;
    @Input() achievedPoints?: number;
    @Input() exerciseGroupId?: number;
    @Input() exerciseTitle?: string;
    @Input() exerciseMaxPoints?: number;
    @Input() exerciseBonusPoints?: number;
    @Input() includedInOverallScore?: IncludedInOverallScore;

    courseId: number;
    studentParticipation: StudentParticipation;
    openingAssessmentEditorForNewSubmission = false;
    readonly ExerciseType = ExerciseType;
    getIcon = getIcon;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        if (this.course && this.course.id) {
            this.courseId = this.course.id!;
        }
    }

    /**
     * get the link for the assessment of a specific submission of the current exercise
     *
     */
    getAssessmentLink() {
        let route;
        if (!this.exerciseId || !this.exerciseType) {
            return;
        }

        if (this.submission) {
            this.openingAssessmentEditorForNewSubmission = true;
            route = getLinkToSubmissionAssessment(
                this.exerciseType!,
                this.courseId,
                this.exerciseId,
                this.studentParticipationId,
                this.submission!.id!,
                this.examId,
                this.exerciseGroupId,
                this.result?.id,
            );
            this.openingAssessmentEditorForNewSubmission = false;
        }
        return route;
    }

    /**
     * Gets the bonus points from the given exercise according to its includedInOverallScore value.
     */
    getBonusPoints(): number | undefined {
        switch (this.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.exerciseBonusPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.exerciseMaxPoints;
            default:
                return 0;
        }
    }

    /**
     * Gets the max points from the given exercise according to its includedInOverallScore value.
     * @param exercise relevant exercise
     */
    getMaxPoints(): number | undefined {
        switch (this.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.exerciseMaxPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
            default:
                return 0;
        }
    }
}
