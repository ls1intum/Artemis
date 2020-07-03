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

    collapsedSubmissionIds: number[] = [];

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
     * returns the students submission for the specific exercise
     */
    getSubmissionForExercise(exercise: Exercise): Submission {
        return exercise.studentParticipations[0].submissions[0];
    }

    /**
     * @param exercise
     * returns the students submission for the specific exercise
     */
    getParticipationForProgrammingExercise(exercise: Exercise): Participation {
        return exercise.studentParticipations[0];
    }

    /**
     * checks collapse control of exercise cards depending on submissionId
     */
    isCollapsed(exercise: Exercise): boolean {
        const submission = this.getSubmissionForExercise(exercise);
        if (submission && submission.id) {
            const submissionId = submission.id;
            return this.collapsedSubmissionIds.includes(submissionId);
        }
        return false;
    }

    /**
     * adds collapse control of exercise cards depending on submissionId
     * @param exercise the exercise for which the submission should be collapsed
     */
    toggleCollapseSubmission(exercise: Exercise): void {
        const submission = this.getSubmissionForExercise(exercise);
        if (submission && submission.id) {
            const submissionId = submission.id;
            const collapsed = this.isCollapsed(exercise);
            if (collapsed) {
                this.collapsedSubmissionIds = this.collapsedSubmissionIds.filter((id) => id !== submissionId);
            } else {
                this.collapsedSubmissionIds.push(submissionId);
            }
        }
    }
}
