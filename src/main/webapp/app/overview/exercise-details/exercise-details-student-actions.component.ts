import { Component, HostBinding, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus, hasExerciseDueDatePassed, hasStudentParticipations } from 'app/entities/exercise';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ProgrammingExerciseStudentParticipation, hasResults } from 'app/entities/participation';
import * as moment from 'moment';
import { CourseExerciseService } from 'app/entities/course';
import { Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core';
import { SourceTreeService } from 'app/components/util/sourceTree.service';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [JhiAlertService, SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent implements OnInit {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly QUIZ_UNINITIALIZED = ParticipationStatus.QUIZ_UNINITIALIZED;
    readonly QUIZ_ACTIVE = ParticipationStatus.QUIZ_ACTIVE;
    readonly QUIZ_SUBMITTED = ParticipationStatus.QUIZ_SUBMITTED;
    readonly QUIZ_NOT_STARTED = ParticipationStatus.QUIZ_NOT_STARTED;
    readonly QUIZ_NOT_PARTICIPATED = ParticipationStatus.QUIZ_NOT_PARTICIPATED;
    readonly QUIZ_FINISHED = ParticipationStatus.QUIZ_FINISHED;
    readonly UNINITIALIZED = ParticipationStatus.UNINITIALIZED;
    readonly INITIALIZED = ParticipationStatus.INITIALIZED;
    readonly INACTIVE = ParticipationStatus.INACTIVE;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;

    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;

    public repositoryPassword: string;
    public wasCopied = false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private accountService: AccountService,
        private sourceTreeService: SourceTreeService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.accountService.identity().then(user => {
            // Only load password if current user login starts with 'edx_' or 'u4i_'
            if (user && user.login && (user.login.startsWith('edx_') || user.login.startsWith('u4i_'))) {
                this.getRepositoryPassword();
            }
        });
    }

    participationStatus(): ParticipationStatus {
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_NOT_STARTED;
            } else if (
                !hasStudentParticipations(this.exercise) &&
                (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate!).isAfter(moment())) &&
                quizExercise.visibleToStudents
            ) {
                return ParticipationStatus.QUIZ_UNINITIALIZED;
            } else if (!hasStudentParticipations(this.exercise)) {
                return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
            } else if (this.exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED && moment(this.exercise.dueDate!).isAfter(moment())) {
                return ParticipationStatus.QUIZ_ACTIVE;
            } else if (this.exercise.studentParticipations[0].initializationState === InitializationState.FINISHED && moment(this.exercise.dueDate!).isAfter(moment())) {
                return ParticipationStatus.QUIZ_SUBMITTED;
            } else {
                if (!hasResults(this.exercise.studentParticipations[0])) {
                    return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
                }
                return ParticipationStatus.QUIZ_FINISHED;
            }
        } else if (
            (this.exercise.type === ExerciseType.MODELING || this.exercise.type === ExerciseType.TEXT || this.exercise.type === ExerciseType.FILE_UPLOAD) &&
            hasStudentParticipations(this.exercise)
        ) {
            const participation = this.exercise.studentParticipations[0];
            if (participation.initializationState === InitializationState.INITIALIZED) {
                if (hasExerciseDueDatePassed(this.exercise)) {
                    return ParticipationStatus.EXERCISE_ACTIVE;
                } else {
                    return ParticipationStatus.EXERCISE_MISSED;
                }
            } else if (participation.initializationState === InitializationState.FINISHED) {
                return ParticipationStatus.EXERCISE_SUBMITTED;
            } else {
                return ParticipationStatus.UNINITIALIZED;
            }
        }

        if (!hasStudentParticipations(this.exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (this.exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    repositoryUrl(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
    }

    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(quizExercise.dueDate!).isBefore(moment());
    }

    isOnlineEditorAllowed(): boolean {
        return (this.exercise as ProgrammingExercise).allowOnlineEditor;
    }

    publishBuildPlanUrl(): boolean {
        return (this.exercise as ProgrammingExercise).publishBuildPlanUrl;
    }

    onCopyFailure() {
        console.log('copy fail!');
    }

    onCopySuccess() {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 3000);
    }

    startExercise() {
        this.exercise.loading = true;

        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/quiz', this.exercise.id]);
        }

        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = this.participationStatus();
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('artemisApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return this.sourceTreeService.buildSourceTreeUrl(cloneUrl);
    }

    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = this.participationStatus();
                    }
                },
                error => {
                    console.log('Error: ' + error.status + ' ' + error.message);
                    this.jhiAlertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            );
    }

    startPractice() {
        return this.router.navigate(['/quiz', this.exercise.id, 'practice']);
    }

    getRepositoryPassword() {
        this.sourceTreeService.getRepositoryPassword().subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }
}
