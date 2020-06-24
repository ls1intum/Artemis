import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import * as moment from 'moment';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Submission } from 'app/entities/submission.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit, OnDestroy {
    @ViewChild(ExamSubmissionComponent, { static: false })
    currentSubmissionComponent: ExamSubmissionComponent;

    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    courseId: number;
    examId: number;

    // needed, because studentExam is downloaded only when exam is started
    exam: Exam;
    studentExam: StudentExam;

    activeExercise: Exercise;
    unsavedChanges = false;
    disconnected = false;

    examConfirmed = false;

    /**
     * Websocket channels
     */
    onConnected: () => void;
    onDisconnected: () => void;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    _reload = true;

    constructor(
        private courseCalculationService: CourseScoreCalculationService,
        private jhiWebsocketService: JhiWebsocketService,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
        private modelingSubmissionService: ModelingSubmissionService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private textSubmissionService: TextSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
    ) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);
            this.examParticipationService.loadExam(this.courseId, this.examId).subscribe((exam) => (this.exam = exam));
        });
        this.initLiveMode();
    }

    /**
     * exam start text confirmed and name entered, start button clicked and exam avtive
     */
    examStarted(studentExam: StudentExam) {
        if (studentExam) {
            // init studentExam and activeExercise
            this.studentExam = studentExam;
            this.activeExercise = studentExam.exercises[0];
            // initialize all submissions as synced
            this.studentExam.exercises.forEach((exercise) => {
                exercise.studentParticipations.forEach((participation) => {
                    if (participation.submissions && participation.submissions.length > 0) {
                        participation.submissions.forEach((submission) => {
                            submission.isSynced = true;
                        });
                    } else {
                        // create empty fallback submission
                        let submission;
                        switch (exercise.type) {
                            case ExerciseType.TEXT:
                                submission = new TextSubmission();
                                break;
                            case ExerciseType.FILE_UPLOAD:
                                submission = new FileUploadSubmission();
                                break;
                            case ExerciseType.MODELING:
                                submission = new ModelingSubmission();
                                break;
                            case ExerciseType.PROGRAMMING:
                                submission = new ProgrammingSubmission();
                                break;
                            case ExerciseType.QUIZ:
                                submission = new QuizSubmission();
                                break;
                        }
                        submission.isSynced = true;
                        participation.submissions = [submission];
                    }
                });
            });
        }
        this.examConfirmed = true;
        this.startAutoSaveTimer();
    }

    /**
     * start AutoSaveTimer
     */
    public startAutoSaveTimer(): void {
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= 60) {
                this.triggerSave();
            }
        }, 1000);
    }

    /**
     * check if exam is over
     */
    isOver(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.endDate ? this.exam.endDate.isBefore(moment()) : false;
    }

    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.visibleDate ? this.exam.visibleDate.isBefore(moment()) : false;
    }

    /**
     * check if exam has started
     */
    isActive(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.startDate ? this.exam.startDate.isBefore(moment()) : false;
    }

    ngOnDestroy(): void {
        window.clearInterval(this.autoSaveInterval);
    }

    initLiveMode() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });
    }

    /**
     * update the current exercise from the navigation
     * @param {Exercise} exercise
     */
    onExerciseChange(exercise: Exercise): void {
        this.triggerSave();
        this.activeExercise = exercise;
        this.reloadSubmissionComponent();
    }

    private reloadSubmissionComponent() {
        setTimeout(() => (this._reload = false));
        setTimeout(() => (this._reload = true));
    }

    /**
     * We support 3 different cases here:
     * 1) Navigate between two exercises
     * 2) Click on Save & Continue
     * 3) The 60s timer was triggered
     *      --> in this case, we can even save all submissions with isSynced = true
     */
    triggerSave() {
        // before the request, we would mark the submission as isSynced = true
        // right after the response - in case it was successful - we mark the submission as isSynced = false
        this.autoSaveTimer = 0;

        if (this.currentSubmissionComponent?.hasUnsavedChanges()) {
            this.currentSubmissionComponent.updateSubmissionFromView();
        }

        // goes through all exercises and checks if there are unsynched submissions
        const submissionsToSync: { exercise: Exercise; submission: Submission }[] = [];
        this.studentExam.exercises.forEach((exercise: Exercise) => {
            exercise.studentParticipations.forEach((participation) => {
                participation.submissions
                    .filter((submission) => !submission.isSynced)
                    .forEach((unsynchedSubmission) => {
                        submissionsToSync.push({ exercise, submission: unsynchedSubmission });
                    });
            });
        });

        // if no connection available -> don't try to sync
        if (!this.disconnected) {
            submissionsToSync.forEach((submissionToSync: { exercise: Exercise; submission: Submission }) => {
                switch (submissionToSync.exercise.type) {
                    case ExerciseType.TEXT:
                        this.textSubmissionService
                            .update(submissionToSync.submission as TextSubmission, this.activeExercise.id)
                            .subscribe(() => (submissionToSync.submission.isSynced = true));
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        // TODO: works differently than other services
                        // this.fileUploadSubmissionService;
                        break;
                    case ExerciseType.MODELING:
                        this.modelingSubmissionService
                            .update(submissionToSync.submission as ModelingSubmission, this.activeExercise.id)
                            .subscribe(() => (submissionToSync.submission.isSynced = true));
                        break;
                    case ExerciseType.PROGRAMMING:
                        // TODO: works differently than other services
                        // this.programmingSubmissionService;
                        break;
                    case ExerciseType.QUIZ:
                        this.examParticipationService
                            .updateQuizSubmission(submissionToSync.exercise.id, submissionToSync.submission as QuizSubmission)
                            .subscribe(() => (submissionToSync.submission.isSynced = true));
                        break;
                }
            });
        }

        // overwrite studentExam in localStorage
        this.examParticipationService.saveStudentExamToLocalStorage(this.courseId, this.examId, this.studentExam);
    }
}
