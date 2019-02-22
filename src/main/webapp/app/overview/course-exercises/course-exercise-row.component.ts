import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ParticipationService } from 'app/entities/participation';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Course, CourseExerciseService } from 'app/entities/course';
import { AccountService, WindowRef } from 'app/core';
import { Router, ActivatedRoute } from '@angular/router';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

export interface ExerciseIcon {
    faIcon: string;
    tooltip: string;
}

@Component({
    selector: 'jhi-course-exercise-row',
    templateUrl: './course-exercise-row.component.html',
    styleUrls: ['./course-exercise-row.scss']
})
export class CourseExerciseRowComponent implements OnInit {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    @HostBinding('class') classes = 'exercise-row';
    @Input() exercise: Exercise;
    @Input() course: Course;
    public repositoryPassword: string;
    public wasCopied = false;

    constructor(private accountService: AccountService,
                private jhiAlertService: JhiAlertService,
                private $window: WindowRef,
                private participationService: ParticipationService,
                private httpClient: HttpClient,
                private router: Router,
                private route: ActivatedRoute,
                private courseExerciseService: CourseExerciseService) {
    }

    ngOnInit() {
        this.accountService.identity().then(user => {
            // Only load password if current user login starts with 'edx'
            if (user && user.login && user.login.startsWith('edx')) {
                this.getRepositoryPassword();
            }
        });
        this.exercise.participationStatus = this.participationStatus(this.exercise);
        if (this.exercise.participations.length > 0) {
            this.exercise.participations[0].exercise = this.exercise;
        }
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            quizExercise.isActiveQuiz = this.isActiveQuiz(this.exercise);

            quizExercise.isPracticeModeAvailable =
                quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(this.exercise.dueDate).isBefore(moment());
            this.exercise = quizExercise;
        }
    }

    get exerciseRouterLink(): string {
        if (this.exercise.type === ExerciseType.MODELING) {
            return `/course/${this.course.id}/exercise/${this.exercise.id}/assessment`;
        } else if (this.exercise.type === ExerciseType.TEXT) {
            return `/text/${this.exercise.id}/assessment`;

        } else {
            return;
        }
    }

    getRepositoryPassword() {
        this.httpClient.get(`${SERVER_API_URL}/api/account/password`).subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }

    getUrgentClass(date: Moment): string {
        if (!date) {
            return;
        }
        const remainingDays = date.diff(moment(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        } else {
            return;
        }
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise as ProgrammingExercise;
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }

    get exerciseIcon(): ExerciseIcon {
        switch(this.exercise.type) {
            case this.PROGRAMMING:
                return {
                    faIcon: 'keyboard',
                    tooltip: 'This is a programming exercise'
                };
            case this.MODELING:
                return {
                    faIcon: 'project-diagram',
                    tooltip: 'This is a modeling exercise'
                };
            case this.QUIZ:
                return {
                    faIcon: 'check-double',
                    tooltip: 'This is a quiz exercise'
                };
            case this.TEXT:
                return {
                    faIcon: 'font',
                    tooltip: 'This is a text exercise'
                };
            case this.FILE_UPLOAD:
                return {
                    faIcon: 'file-upload',
                    tooltip: 'This is a file upload exercise'
                };
            default:
                return;

        }
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=https://repobruegge.in.tum.de';
    }

    goToBuildPlan(participation: Participation) {
        this.participationService.buildPlanWebUrl(participation.id).subscribe(res => {
            this.$window.nativeWindow.open(res.url);
        });
    }

    isActiveQuiz(exercise: Exercise) {
        return (
            exercise.participationStatus === ParticipationStatus.QUIZ_UNINITIALIZED ||
            exercise.participationStatus === ParticipationStatus.QUIZ_ACTIVE ||
            exercise.participationStatus === ParticipationStatus.QUIZ_SUBMITTED
        );
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

    participationStatus(exercise: Exercise): ParticipationStatus {
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate).isAfter(moment())) && quizExercise.visibleToStudents) {
                return ParticipationStatus.QUIZ_NOT_STARTED;
            } else if (
                !this.hasParticipations(exercise) &&
                (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate).isAfter(moment())) &&
                quizExercise.visibleToStudents
            ) {
                return ParticipationStatus.QUIZ_UNINITIALIZED;
            } else if (!this.hasParticipations(exercise)) {
                return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
            } else if (
                exercise.participations[0].initializationState === InitializationState.INITIALIZED &&
                moment(exercise.dueDate).isAfter(moment())
            ) {
                return ParticipationStatus.QUIZ_ACTIVE;
            } else if (
                exercise.participations[0].initializationState === InitializationState.FINISHED &&
                moment(exercise.dueDate).isAfter(moment())
            ) {
                return ParticipationStatus.QUIZ_SUBMITTED;
            } else {
                if (!this.hasResults(exercise.participations[0])) {
                    return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
                }
                return ParticipationStatus.QUIZ_FINISHED;
            }
        } else if ((exercise.type === ExerciseType.MODELING || exercise.type === ExerciseType.TEXT) && this.hasParticipations(exercise)) {
            const participation = exercise.participations[0];
            if (
                participation.initializationState === InitializationState.INITIALIZED ||
                participation.initializationState === InitializationState.FINISHED
            ) {
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

    startExercise(exercise: Exercise) {
        exercise.loading = true;

        if (exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/quiz', exercise.id]);
        }

        this.courseExerciseService
            .startExercise(this.course.id, exercise.id)
            .finally(() => (exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        exercise.participations = [participation];
                        exercise.participationStatus = this.participationStatus(exercise);
                    }
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('arTeMiSApp.exercise.personalRepository');
                    }
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('arTeMiSApp.exercise.startError');
                }
            );
    }

    resumeExercise(exercise: Exercise) {
        exercise.loading = true;
        this.courseExerciseService
            .resumeExercise(this.course.id, exercise.id)
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

    showDetails(event: any) {
        if (!(event.target.closest('jhi-exercise-details-student-actions') && event.target.closest('.btn'))) {
            this.router.navigate([this.exercise.id], {relativeTo: this.route});
        }
    }

}
