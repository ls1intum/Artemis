import { Component, HostBinding, Input, OnDestroy, OnInit } from '@angular/core';
import {
    Exercise,
    ExerciseCategory,
    ExerciseService,
    ExerciseType,
    getIcon,
    getIconTooltip,
    hasExerciseDueDatePassed,
    hasStudentParticipations,
    ParticipationStatus,
} from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { hasResults, InitializationState, ParticipationService, ParticipationWebsocketService, StudentParticipation } from 'app/entities/participation';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Subscription } from 'rxjs/Subscription';
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
    @Input() hasGuidedTour: boolean;

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
            this.exercise.studentParticipations = cachedParticipations;
        }
        this.participationWebsocketService.addExerciseForNewParticipation(this.exercise.id);
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise.id === this.exercise.id) {
                this.exercise.studentParticipations =
                    this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0
                        ? this.exercise.studentParticipations.map(el => {
                              return el.id === changedParticipation.id ? changedParticipation : el;
                          })
                        : [changedParticipation];
                this.exercise.participationStatus = this.participationStatus(this.exercise);
            }
        });
        this.exercise.participationStatus = this.participationStatus(this.exercise);
        if (this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0) {
            this.exercise.studentParticipations[0].exercise = this.exercise;
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

    /**
     * Handles the evaluation of participation status.
     *
     * @param exercise
     * @return {ParticipationStatus}
     */
    participationStatus(exercise: Exercise): ParticipationStatus {
        // Evaluate the participation status for quiz exercises.
        if (exercise.type === ExerciseType.QUIZ) {
            return this.participationStatusForQuizExercise(exercise);
        }

        // Evaluate the participation status for modeling, text and file upload exercises if the exercise has participations.
        if ((exercise.type === ExerciseType.MODELING || exercise.type === ExerciseType.TEXT || exercise.type === ExerciseType.FILE_UPLOAD) && hasStudentParticipations(exercise)) {
            return this.participationStatusForModelingTextFileUploadExercise(exercise);
        }

        // The following evaluations are relevant for programming exercises in general and for modeling, text and file upload exercises that don't have participations.
        if (!hasStudentParticipations(exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    /**
     * Handles the evaluation of participation status for quiz exercises.
     *
     * @param exercise
     * @return {ParticipationStatus}
     */
    private participationStatusForQuizExercise(exercise: Exercise): ParticipationStatus {
        const quizExercise = exercise as QuizExercise;
        if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
            return ParticipationStatus.QUIZ_NOT_STARTED;
        } else if (!hasStudentParticipations(exercise) && (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
            return ParticipationStatus.QUIZ_UNINITIALIZED;
        } else if (!hasStudentParticipations(exercise)) {
            return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
        } else if (exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED && moment(exercise.dueDate!).isAfter(moment())) {
            return ParticipationStatus.QUIZ_ACTIVE;
        } else if (exercise.studentParticipations[0].initializationState === InitializationState.FINISHED && moment(exercise.dueDate!).isAfter(moment())) {
            return ParticipationStatus.QUIZ_SUBMITTED;
        } else {
            return !hasResults(exercise.studentParticipations[0]) ? ParticipationStatus.QUIZ_NOT_PARTICIPATED : ParticipationStatus.QUIZ_FINISHED;
        }
    }

    /**
     * Handles the evaluation of participation status for modeling, text and file upload exercises if the exercise has participations.
     *
     * @param exercise
     * @return {ParticipationStatus}
     */
    private participationStatusForModelingTextFileUploadExercise(exercise: Exercise): ParticipationStatus {
        const participation = exercise.studentParticipations[0];

        // An exercise is active (EXERCISE_ACTIVE) if it is initialized and has not passed its due date. The more detailed evaluation of active exercises takes place in the result component.
        // An exercise was missed (EXERCISE_MISSED) if it is initialized and has passed its due date (due date lies in the past).
        if (participation.initializationState === InitializationState.INITIALIZED) {
            return hasExerciseDueDatePassed(exercise) ? ParticipationStatus.EXERCISE_MISSED : ParticipationStatus.EXERCISE_ACTIVE;
        } // An exercise was submitted (EXERCISE_SUBMITTED) if the corresponding InitializationState is set to FINISHED
        else if (participation.initializationState === InitializationState.FINISHED) {
            return ParticipationStatus.EXERCISE_SUBMITTED;
        } else {
            return ParticipationStatus.UNINITIALIZED;
        }
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
