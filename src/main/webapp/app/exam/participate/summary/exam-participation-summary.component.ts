import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';
import * as moment from 'moment';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exam-participation-summary',
    templateUrl: './exam-participation-summary.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss'],
})
export class ExamParticipationSummaryComponent implements OnInit {
    // make constants available to html for comparison
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;

    @Input()
    studentExam: StudentExam;

    @Input()
    instructorView = false;

    collapsedExerciseIds: number[] = [];

    courseId: number;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialise the courseId from the current url
     */
    ngOnInit(): void {
        // courseId is not part of the exam or the exercise
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }

    getIcon(exerciseType: ExerciseType) {
        return getIcon(exerciseType);
    }

    get resultsPublished() {
        return this.studentExam.exam.publishResultsDate && moment(this.studentExam.exam.publishResultsDate).isBefore(moment());
    }

    /**
     * called for exportPDF Button
     */
    printPDF() {
        // expand all exercises before printing
        this.collapsedExerciseIds = [];
        setTimeout(() => window.print());
    }

    public generateLink(exercise: Exercise) {
        return ['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', exercise.studentParticipations[0].id];
    }

    /**
     * @param exercise
     * returns the students submission for the exercise, null if no participation could be found
     */
    getSubmissionForExercise(exercise: Exercise): Submission | null {
        if (
            exercise &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].submissions &&
            exercise.studentParticipations[0].submissions.length > 0
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
     * checks collapse control of exercise cards depending on exerciseId
     */
    isCollapsed(exerciseId: number): boolean {
        return this.collapsedExerciseIds.includes(exerciseId);
    }

    /**
     * @param exerciseId
     * adds collapse control of exercise cards depending on exerciseId
     * @param exerciseId the exercise for which the submission should be collapsed
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
