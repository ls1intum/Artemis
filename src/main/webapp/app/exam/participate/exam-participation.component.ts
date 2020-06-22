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

    constructor(
        private courseCalculationService: CourseScoreCalculationService,
        private jhiWebsocketService: JhiWebsocketService,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
    ) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.studentExamSubscription = this.examParticipationService.studentExam$.subscribe((studentExam) => {
            this.studentExam = studentExam;
            this.activeExercise = studentExam.exercises[0];
        });
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);
            // set examId and courseId in service
            this.examParticipationService.courseId = this.courseId;
            this.examParticipationService.examId = this.examId;
        });

        // initializes student exam (gets student exam from server/localStorage)
        this.examParticipationService.initStudentExam();
        this.initLiveMode();
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
    }

    initLiveMode() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            if (this.disconnected) {
                // if the disconnect happened during the live exam and there are unsaved changes, we trigger a selection changed event to save the submission on the server
                if (this.unsavedChanges) {
                    // ToDo: save submission on server
                }
            }
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
        // TODO: trigger save for activeExercise
        this.triggerSave();
        this.activeExercise = exercise;
    }

    /**
     * We support 3 different cases here:
     * 1) Navigate between two exercises
     * 2) Click on Save & Continue
     * 3) The 60s timer was triggered (TODO: move logic from exam.participation.service.ts to here)
     *      --> in this case, we can even save all submissions with isSynced = true
     */
    triggerSave() {
        // TODO: for the active exercise: check the currentSubmissionComponent if it has changes
        // TODO: if yes, invoke updateSubmissionFromView
        // TODO: then save these changes on the server
        // before the request, we would mark the submission as isSynced = true
        // right after the response - in case it was successfull - we mark the submission as isSynced = false
    }
}
