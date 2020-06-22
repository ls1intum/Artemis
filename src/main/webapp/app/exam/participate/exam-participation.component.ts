import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import * as moment from 'moment';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { forkJoin } from 'rxjs';
import { Submission } from 'app/entities/submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit, OnDestroy {
    // TODO: make sure this works https://stackoverflow.com/questions/36842401/angular2-viewchild-from-typescript-base-abstract-class
    @ViewChild(ExamSubmissionComponent, { static: false })
    currentSubmissionComponent: ExamSubmissionComponent;

    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;

    private paramSubscription: Subscription;
    private studentExamSubscription: Subscription;

    courseId: number;
    examId: number;

    studentExam: StudentExam;
    activeExercise: Exercise;
    unsavedChanges = false;
    disconnected = false;

    // TODO: save on server
    examConfirmed = false;

    /**
     * Websocket channels
     */
    onConnected: () => void;
    onDisconnected: () => void;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    private submissionSyncList: Submission[] = [];

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
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);

            this.studentExamSubscription = this.examParticipationService.loadStudentExam(this.courseId, this.examId).subscribe((studentExam) => {
                this.studentExam = studentExam;
                this.activeExercise = studentExam.exercises[0];
                // initialize all submissions as synced
                this.studentExam.exercises.forEach((exercise) => {
                    exercise.studentParticipations.forEach((participation) => {
                        participation.submissions.forEach((submission) => {
                            submission.isSynced = true;
                        });
                    });
                });
            });
        });

        this.startAutoSaveTimer();
        this.initLiveMode();
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
        if (!this.studentExam?.exam) {
            return false;
        }
        return this.studentExam.exam.endDate ? moment(this.studentExam.exam.endDate).isBefore(moment()) : false;
    }

    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (!this.studentExam?.exam) {
            return false;
        }
        return this.studentExam.exam.visibleDate ? moment(this.studentExam.exam.visibleDate).isBefore(moment()) : false;
    }

    /**
     * check if exam has started
     */
    isActive(): boolean {
        if (!this.studentExam?.exam) {
            return false;
        }
        return this.studentExam.exam.startDate ? moment(this.studentExam.exam.startDate).isBefore(moment()) : false;
    }

    ngOnDestroy(): void {
        this.paramSubscription.unsubscribe();
        this.studentExamSubscription.unsubscribe();
        window.clearInterval(this.autoSaveTimer);
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
    }

    /**
     * We support 3 different cases here:
     * 1) Navigate between two exercises
     * 2) Click on Save & Continue
     * 3) The 60s timer was triggered
     *      --> in this case, we can even save all submissions with isSynced = true
     */
    triggerSave() {
        // TODO: for the active exercise: check the currentSubmissionComponent if it has changes
        // TODO: if yes, invoke updateSubmissionFromView
        // TODO: then save these changes on the server
        // before the request, we would mark the submission as isSynced = true
        // right after the response - in case it was successfull - we mark the submission as isSynced = false
        this.autoSaveTimer = 0;
        forkJoin(
            this.submissionSyncList.map((submission) => {
                const examExercise = this.studentExam.exercises.find((exercise) =>
                    exercise.studentParticipations.some((participation) => participation.submissions.some((examSubmission) => examSubmission.id === submission.id)),
                );
                if (examExercise) {
                    switch (examExercise.type) {
                        case ExerciseType.TEXT:
                            return this.textSubmissionService.update(submission as TextSubmission, examExercise.id);
                        case ExerciseType.FILE_UPLOAD:
                            // TODO: works differently than other services
                            return this.fileUploadSubmissionService;
                        case ExerciseType.MODELING:
                            return this.modelingSubmissionService.update(submission as ModelingSubmission, examExercise.id);
                        case ExerciseType.PROGRAMMING:
                            // TODO: works differently than other services
                            return this.programmingSubmissionService;
                        case ExerciseType.QUIZ:
                            // TODO find submissionService
                            return null;
                    }
                }
            }),
        ).subscribe(() => {
            // clear sync list
            this.submissionSyncList = [];
        });
    }
}
