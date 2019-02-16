import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ParticipationService } from 'app/entities/participation';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Course, CourseExerciseService } from 'app/entities/course';
import { AccountService, WindowRef } from 'app/core';
import { Router } from '@angular/router';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-course-exercise-row-actions',
    templateUrl: './course-exercise-row-actions.component.html'
})
export class CourseExerciseRowActionsComponent {
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
    readonly MODELING_EXERCISE = ParticipationStatus.MODELING_EXERCISE;
    readonly TEXT_EXERCISE = ParticipationStatus.TEXT_EXERCISE;
    readonly UNINITIALIZED = ParticipationStatus.UNINITIALIZED;
    readonly INITIALIZED = ParticipationStatus.INITIALIZED;
    readonly INACTIVE = ParticipationStatus.INACTIVE;

    @Input() exercise: Exercise;

    public wasCopied: boolean = false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private router: Router,
    ) {
    }

    participationStatus(): ParticipationStatus {
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_NOT_STARTED;
            } else if (
                !this.hasParticipations(this.exercise) &&
                (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate).isAfter(moment())) &&
                quizExercise.visibleToStudents
            ) {
                return ParticipationStatus.QUIZ_UNINITIALIZED;
            } else if (!this.hasParticipations(this.exercise)) {
                return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
            } else if (
                this.exercise.participations[0].initializationState === InitializationState.INITIALIZED &&
                moment(this.exercise.dueDate).isAfter(moment())
            ) {
                return ParticipationStatus.QUIZ_ACTIVE;
            } else if (
                this.exercise.participations[0].initializationState === InitializationState.FINISHED &&
                moment(this.exercise.dueDate).isAfter(moment())
            ) {
                return ParticipationStatus.QUIZ_SUBMITTED;
            } else {
                if (!this.hasResults(this.exercise.participations[0])) {
                    return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
                }
                return ParticipationStatus.QUIZ_FINISHED;
            }
        } else if ((this.exercise.type === ExerciseType.MODELING || this.exercise.type === ExerciseType.TEXT) && this.hasParticipations(this.exercise)) {
            const participation = this.exercise.participations[0];
            if (
                participation.initializationState === InitializationState.INITIALIZED ||
                participation.initializationState === InitializationState.FINISHED
            ) {
                return this.exercise.type === ExerciseType.MODELING ? ParticipationStatus.MODELING_EXERCISE : ParticipationStatus.TEXT_EXERCISE;
            }
        }

        if (!this.hasParticipations(this.exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (this.exercise.participations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    hasParticipations(exercise: Exercise): boolean {
        return exercise.participations && exercise.participations.length > 0;
    }

    hasResults(participation: Participation): boolean {
        return participation.results && participation.results.length > 0;
    }

    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(quizExercise.dueDate).isBefore(moment());
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
            .startExercise(this.exercise.course.id, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.participations = [participation];
                        this.exercise.participationStatus = this.participationStatus();
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('arTeMiSApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('arTeMiSApp.exercise.startError');
                }
            );
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=https://repobruegge.in.tum.de';
    }

    resumeExercise(exercise: Exercise) {
        exercise.loading = true;
        this.courseExerciseService
            .resumeExercise(this.exercise.course.id, this.exercise.id)
            .finally(() => (exercise.loading = false))
            .subscribe(
                () => true,
                error => {
                    console.log('Error: ' + error.status + ' ' + error.message);
                }
            );
    }

    startPractice(exercise: Exercise) {
        return this.router.navigate(['/quiz', exercise.id, 'practice']);
    }
}
