import { Component, Input } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { getIcon } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-participation-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss'],
})
export class ExamParticipationSummaryComponent {
    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;

    @Input()
    studentExam: StudentExam;

    collapsedExerciseIds: number[] = [];

    constructor() {}

    getIcon(exerciseType: ExerciseType) {
        return getIcon(exerciseType);
    }

    /**
     * called for exportPDF Button
     */
    printPDF() {
        // expand all exercises before printing
        this.collapsedSubmissionIds = [];
        setTimeout(() => window.print());
    }

    /**
     * @param exercise
     * returns the students submission for the exercise, null if no participation could be found
     */
    getSubmissionForExercise(exercise: Exercise): Submission | null {
        if (
            exercise &&
            exercise.studentParticipations &&
            exercise.studentParticipations[0] &&
            exercise.studentParticipations[0].submissions &&
            exercise.studentParticipations[0].submissions[0]
        ) {
            return exercise.studentParticipations[0].submissions[0];
        } else {
            return null;
        }
    }

    /**
     * @param exercise
     * returns the students submission for the exercise, null if no participation could be found
     */
    getParticipationForExercise(exercise: Exercise): Participation | null {
        if (exercise.studentParticipations && exercise.studentParticipations[0]) {
            return exercise.studentParticipations[0];
        } else {
            return null;
        }
    }

    /**
     * @param exerciseId
     * checks collapse control of exercise cards depending on submissionId
     */
    isCollapsed(exerciseId: number): boolean {
        return this.collapsedExerciseIds.includes(exerciseId);
    }

    /**
     * @param exerciseId
     * adds collapse control of exercise cards depending on submissionId
     */
    toggleCollapseExercise(exerciseId: number): void {
        const collapsed = this.isCollapsed(exerciseId);
        if (collapsed) {
            this.collapsedExerciseIds = this.collapsedExerciseIds.filter((id) => id !== exerciseId);
        } else {
            this.collapsedExerciseIds.push(exerciseId);
        }
    }
}
