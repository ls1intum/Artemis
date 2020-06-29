import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { getIcon } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

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
    exam: Exam;

    collapsedSubmissionIds: number[] = [];

    courseId: number;
    examId: number;

    constructor(
        private examParticipationService: ExamParticipationService,
        private codeEditorRepositoryFileService: CodeEditorRepositoryFileService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        // if the studentExam is not available we retrieve it
        if (!this.studentExam) {
            this.loadStudentSubmission();
        }
    }

    loadStudentSubmission() {
        this.examParticipationService.loadStudentExam(this.exam.course.id, this.exam.id).subscribe((studentExam: StudentExam) => {
            this.examParticipationService.saveStudentExamToLocalStorage(this.exam.course.id, this.exam.id, studentExam);
            // TODO: In case of programming exercises with a programming submission (and online editor enabled) save the
            // files in the participation to the codeEditorRepositoryFileService
            studentExam.exercises.forEach((exercise) => {
                if (exercise.type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).allowOnlineEditor) {
                    exercise.studentParticipations[0] = Object.assign(new ProgrammingExerciseStudentParticipation(), exercise.studentParticipations[0]);
                    this.codeEditorRepositoryFileService.setDomain([DomainType.PARTICIPATION, exercise.studentParticipations[0]]);
                }
            });
            this.studentExam = studentExam;
        });
    }

    getIcon(exerciseType: ExerciseType) {
        return getIcon(exerciseType);
    }

    /**
     * called for exportPDF Button
     */
    printPDF() {
        window.print();
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
     * @param submissionId
     * checks collapse control of exercise cards depending on submissionId
     */
    isCollapsed(submissionId: number): boolean {
        return this.collapsedSubmissionIds.includes(submissionId);
    }

    /**
     * @param submissionId
     * adds collapse control of exercise cards depending on submissionId
     */
    toggleCollapseSubmission(submissionId: number): void {
        const collapsed = this.isCollapsed(submissionId);
        if (collapsed) {
            this.collapsedSubmissionIds = this.collapsedSubmissionIds.filter((id) => id !== submissionId);
        } else {
            this.collapsedSubmissionIds.push(submissionId);
        }
    }
}
