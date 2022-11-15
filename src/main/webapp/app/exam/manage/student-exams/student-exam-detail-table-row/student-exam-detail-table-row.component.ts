import { Component, Input, OnChanges } from '@angular/core';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseSubmissionsLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { Course } from 'app/entities/course.model';
import { Result } from 'app/entities/result.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: '[jhi-student-exam-detail-table-row]',
    templateUrl: './student-exam-detail-table-row.component.html',
    providers: [],
})
export class StudentExamDetailTableRowComponent implements OnChanges {
    @Input() exercise: Exercise;
    @Input() examId: number;
    @Input() isTestRun: boolean;
    @Input() course: Course;
    @Input() busy: boolean;
    @Input() studentExam: StudentExam;
    @Input() achievedPointsPerExercise: { [exerciseId: number]: number };

    courseId: number;
    studentParticipation: StudentParticipation;
    submission: Submission;
    result: Result;
    openingAssessmentEditorForNewSubmission = false;
    readonly ExerciseType = ExerciseType;
    getIcon = getIcon;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        if (this.exercise.studentParticipations?.[0]) {
            this.studentParticipation = this.exercise.studentParticipations![0];
            if (this.studentParticipation.submissions?.length! > 0) {
                this.submission = this.studentParticipation.submissions![0];
            }
            if (this.studentParticipation.results?.length! > 0) {
                this.result = this.studentParticipation.results![0];
            }
        }
        if (this.course && this.course.id) {
            this.courseId = this.course.id!;
        }
    }

    /**
     * get the link for the assessment of a specific submission of the current exercise
     * @param exercise
     * @param submission
     * @param resultId
     */
    getAssessmentLink(exercise: Exercise, submission?: Submission, resultId?: number) {
        let route;
        if (!exercise || !exercise.type) {
            return;
        }

        if (exercise.type === ExerciseType.PROGRAMMING) {
            route = getExerciseSubmissionsLink(exercise.type, this.courseId, exercise.id!, this.examId, exercise.exerciseGroup?.id!);
        } else if (submission) {
            this.openingAssessmentEditorForNewSubmission = true;
            route = getLinkToSubmissionAssessment(
                exercise.type,
                this.courseId,
                exercise.id!,
                this.studentParticipation?.id,
                submission.id!,
                this.examId,
                exercise.exerciseGroup?.id!,
                resultId,
            );
            this.openingAssessmentEditorForNewSubmission = false;
        }
        return route;
    }

    /**
     * Gets the bonus points from the given exercise according to its includedInOverallScore value.
     * @param exercise exercise with or without bonus points
     */
    getBonusPoints(exercise?: Exercise): number | undefined {
        if (!exercise) {
            return 0;
        }
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return exercise.bonusPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return exercise.maxPoints;
            default:
                return 0;
        }
    }

    /**
     * Gets the max points from the given exercise according to its includedInOverallScore value.
     * @param exercise relevant exercise
     */
    getMaxPoints(exercise?: Exercise): number | undefined {
        if (!exercise) {
            return 0;
        }
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return exercise.maxPoints;
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
            default:
                return 0;
        }
    }
}
