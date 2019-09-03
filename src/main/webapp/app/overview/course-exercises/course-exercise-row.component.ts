import { Component, HostBinding, Input, OnInit, OnDestroy } from '@angular/core';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType, ParticipationStatus, getIcon, getIconTooltip } from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ParticipationService, ParticipationWebsocketService, StudentParticipation } from 'app/entities/participation';
import * as moment from 'moment';
import { Subscription } from 'rxjs/Subscription';

import { Moment } from 'moment';
import { Course } from 'app/entities/course';
import { AccountService, WindowRef } from 'app/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-course-exercise-row',
    templateUrl: './course-exercise-row.component.html',
    styleUrls: ['./course-exercise-row.scss'],
})
export class CourseExerciseRowComponent implements OnInit, OnDestroy {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    @HostBinding('class') classes = 'exercise-row';
    @Input() exercise: Exercise;
    @Input() course: Course;
    @Input() extendedLink = false;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;
    public exerciseCategories: ExerciseCategory[];
    isAfterAssessmentDueDate: boolean;

    participationUpdateListener: Subscription;

    constructor(
        private accountService: AccountService,
        private jhiAlertService: JhiAlertService,
        private $window: WindowRef,
        private participationService: ParticipationService,
        private exerciseService: ExerciseService,
        private httpClient: HttpClient,
        private router: Router,
        private route: ActivatedRoute,
        private participationWebsocketService: ParticipationWebsocketService,
    ) {}

    ngOnInit() {
        const cachedParticipations = this.participationWebsocketService.getAllParticipationsForExercise(this.exercise.id);
        if (cachedParticipations && cachedParticipations.length > 0) {
            this.exercise.participations = cachedParticipations;
        }
        this.participationWebsocketService.addExerciseForNewParticipation(this.exercise.id);
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise.id === this.exercise.id) {
                this.exercise.participations =
                    this.exercise.participations && this.exercise.participations.length > 0
                        ? this.exercise.participations.map(el => {
                              return el.id === changedParticipation.id ? changedParticipation : el;
                          })
                        : [changedParticipation];
                this.participationStatus(this.exercise);
            }
        });
        this.exercise.participationStatus = this.participationStatus(this.exercise);
        if (this.exercise.participations.length > 0) {
            this.exercise.participations[0].exercise = this.exercise;
        }
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
        this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || moment().isAfter(this.exercise.assessmentDueDate);
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            quizExercise.isActiveQuiz = this.isActiveQuiz(this.exercise);

            quizExercise.isPracticeModeAvailable = quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(this.exercise.dueDate!).isBefore(moment());
            this.exercise = quizExercise;
        }
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
    }

    ngOnDestroy() {
        if (this.participationUpdateListener) {
            this.participationUpdateListener.unsubscribe();
        }
    }

    getUrgentClass(date: Moment | null): string | null {
        if (!date) {
            return null;
        }
        const remainingDays = date.diff(moment(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        } else {
            return null;
        }
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise as ProgrammingExercise;
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }

    isActiveQuiz(exercise: Exercise) {
        return (
            exercise.participationStatus === ParticipationStatus.QUIZ_UNINITIALIZED ||
            exercise.participationStatus === ParticipationStatus.QUIZ_ACTIVE ||
            exercise.participationStatus === ParticipationStatus.QUIZ_SUBMITTED
        );
    }

    participationStatus(exercise: Exercise): ParticipationStatus {
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_NOT_STARTED;
            } else if (!this.hasParticipations(exercise) && (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_UNINITIALIZED;
            } else if (!this.hasParticipations(exercise)) {
                return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
            } else if (exercise.participations[0].initializationState === InitializationState.INITIALIZED && moment(exercise.dueDate!).isAfter(moment())) {
                return ParticipationStatus.QUIZ_ACTIVE;
            } else if (exercise.participations[0].initializationState === InitializationState.FINISHED && moment(exercise.dueDate!).isAfter(moment())) {
                return ParticipationStatus.QUIZ_SUBMITTED;
            } else {
                if (!this.hasResults(exercise.participations[0])) {
                    return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
                }
                return ParticipationStatus.QUIZ_FINISHED;
            }
        } else if ((exercise.type === ExerciseType.MODELING || exercise.type === ExerciseType.TEXT) && this.hasParticipations(exercise)) {
            const participation = exercise.participations[0];
            if (participation.initializationState === InitializationState.INITIALIZED || participation.initializationState === InitializationState.FINISHED) {
                return exercise.type === ExerciseType.MODELING ? ParticipationStatus.MODELING_EXERCISE : ParticipationStatus.TEXT_EXERCISE;
            }
        }

        if (!this.hasParticipations(exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (exercise.participations[0].initializationState === InitializationState.INITIALIZED) {
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

    showDetails(event: any) {
        const isClickOnAction = event.target.closest('jhi-exercise-details-student-actions') && event.target.closest('.btn');
        const isClickResult = event.target.closest('jhi-result') && event.target.closest('.result');
        if (!isClickOnAction && !isClickResult) {
            if (this.extendedLink) {
                this.router.navigate(['overview', this.course.id, 'exercises', this.exercise.id]);
            } else {
                this.router.navigate([this.exercise.id], { relativeTo: this.route });
            }
        }
    }
}
