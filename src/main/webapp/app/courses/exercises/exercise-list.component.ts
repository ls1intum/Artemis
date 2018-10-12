import { Component, HostListener, Input, OnInit, OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { Course, CourseExerciseService } from '../../entities/course';
import { Exercise, ExerciseType, ParticipationStatus } from '../../entities/exercise';
import { Principal } from '../../core';
import { WindowRef } from '../../core/websocket/window.service';
import { NgbModal, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { Router, NavigationStart } from '@angular/router';
import { InitializationState, Participation, ParticipationService } from '../../entities/participation';
import { ParticipationDataProvider } from '../../courses/exercises/participation-data-provider';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { SERVER_API_URL } from '../../app.constants';
import { QuizExercise } from '../../entities/quiz-exercise';
import * as moment from 'moment';

@Pipe({ name: 'showExercise' })
export class ShowExercisePipe implements PipeTransform {
    transform(allExercises: Exercise[], showInactiveExercises: boolean) {
        return allExercises.filter(
            exercise =>
                showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || !exercise.dueDate || exercise.dueDate > moment()
        );
    }
}

@Component({
    selector: 'jhi-exercise-list',
    templateUrl: './exercise-list.component.html',
    providers: [JhiAlertService, WindowRef, ParticipationService, CourseExerciseService, NgbModal]
})
export class ExerciseListComponent implements OnInit, OnDestroy {
    // Make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    _course: Course;
    routerSubscription: Subscription;

    @Input()
    get course(): Course {
        return this._course;
    }
    set course(course: Course) {
        this._course = course;
        if (course.exercises) {
            // exercises already included in data, no need to load them
            this.initExercises(course.exercises);
        } else {
            this.courseExerciseService
                .findAllExercises(course.id, {
                    courseId: course.id,
                    withLtiOutcomeUrlExisting: true
                })
                .subscribe(exercises => {
                    this.initExercises(exercises.body);
                });
        }
    }
    @Input()
    filterByExerciseId: number;

    /*
     * IMPORTANT NOTICE:
     * The Angular team and many experienced Angular developers strongly recommend that you move filtering and sorting
     * logic into the component itself. [...] Any capabilities that you would have put in a pipe and shared across
     * the app can be written in a filtering/sorting service and injected into the component.
     */

    // Exercises are sorted by dueDate
    exercises: Exercise[];
    now = Date.now();
    numOfInactiveExercises = 0;
    showInactiveExercises = false;
    private repositoryPassword: string;
    lastPopoverRef: NgbPopover;

    constructor(
        private jhiAlertService: JhiAlertService,
        private $window: WindowRef,
        private participationService: ParticipationService,
        private principal: Principal,
        private httpClient: HttpClient,
        private courseExerciseService: CourseExerciseService,
        private router: Router,
        private participationDataProvider: ParticipationDataProvider
    ) {
        // Initialize array to avoid undefined errors
        this.exercises = [];
    }

    ngOnInit(): void {
        this.principal.identity().then(account => {
            // Only load password if current user login starts with 'edx'
            if (account && account.login && account.login.startsWith('edx')) {
                this.getRepositoryPassword();
            }
        });
        // Listen to NavigationStart events; if we are routing to the online editor, we pass the participation (if we find one)
        this.routerSubscription = this.router.events
            .filter(event => event instanceof NavigationStart)
            .subscribe((event: NavigationStart) => {
                if (event.url.startsWith('/editor')) {
                    // Extract participation id from event url and cast to number
                    const participationId = Number(event.url.split('/').slice(-1));
                    // Search through all exercises and the participations within each of them to obtain the target participation
                    const filteredExercise = this.course.exercises.find(
                        exercise =>
                            exercise.participations != null &&
                            exercise.participations.find(exerciseParticipation => exerciseParticipation.id === participationId) !==
                                undefined
                    );
                    if (filteredExercise) {
                        const participation: Participation = filteredExercise.participations.find(
                            currentParticipation => currentParticipation.id === participationId
                        );
                        // Just make sure we have indeed found the desired participation
                        if (participation && participation.id === participationId) {
                            this.participationDataProvider.participationStorage = participation;
                        }
                    }
                }
            });
    }

    initExercises(exercises: Exercise[]) {
        if (this.filterByExerciseId) {
            exercises = exercises.filter(exercise => exercise.id === this.filterByExerciseId);
        }

        this.numOfInactiveExercises = exercises.filter(exercise => !this.showExercise(exercise)).length;

        for (const exercise of exercises) {
            // We assume that exercise has a participation and a result if available because of the explicit courses dashboard call
            exercise.course = this._course;
            exercise.participationStatus = this.participationStatus(exercise);

            if (this.hasParticipations(exercise)) {
                // Reconnect 'participation --> exercise' in case it is needed
                exercise.participations[0].exercise = exercise;
            }

            // If the User is a student: subscribe the release Websocket of every quizExercise
            if (exercise.type === ExerciseType.QUIZ) {
                const quizExercise = exercise as QuizExercise;
                quizExercise.isActiveQuiz = this.isActiveQuiz(exercise);

                quizExercise.isPracticeModeAvailable =
                    quizExercise.isPlannedToStart && quizExercise.isOpenForPractice && moment(exercise.dueDate).isBefore(moment());
            }

            exercise.isAtLeastTutor = this.principal.isAtLeastTutorInCourse(exercise.course);
        }
        exercises.sort((a: Exercise, b: Exercise) => {
            if (a.dueDate === null && b.dueDate === null) {
                return 0;
            } else if (a.dueDate === null) {
                return +1;
            } else if (b.dueDate === null) {
                return -1;
            } else {
                return +a.dueDate.toDate() - +b.dueDate.toDate();
            }
        });
        this.exercises = exercises;
    }

    isActiveQuiz(exercise: Exercise) {
        return (
            exercise.participationStatus === ParticipationStatus.QUIZ_UNINITIALIZED ||
            exercise.participationStatus === ParticipationStatus.QUIZ_ACTIVE ||
            exercise.participationStatus === ParticipationStatus.QUIZ_SUBMITTED
        );
    }

    showExercise(exercise: Exercise) {
        return (
            this.showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || !exercise.dueDate || exercise.dueDate > moment()
        );
    }

    getRepositoryPassword() {
        this.httpClient.get(`${SERVER_API_URL}/api/account/password`).subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
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

    toggleshowInactiveExercises() {
        this.showInactiveExercises = !this.showInactiveExercises;
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=https://repobruegge.in.tum.de';
    }

    goToBuildPlan(participation: Participation) {
        this.participationService.buildPlanWebUrl(participation.id).subscribe(res => {
            this.$window.nativeWindow.open(res.url);
        });
    }

    getClonePopoverTemplate(exercise: Exercise) {
        const html = [
            '<p>Clone your personal repository for this exercise:</p>',
            '<pre style="max-width: 550px;">',
            exercise.participations[0].repositoryUrl,
            '</pre>',
            this.repositoryPassword
                ? '<p>Your password is: <code class="password">' + this.repositoryPassword + '</code> (hover to show)<p>'
                : '',
            '<a class="btn btn-primary btn-sm" href="',
            this.buildSourceTreeUrl(exercise.participations[0].repositoryUrl),
            '">Clone in SourceTree</a>',
            ' <a href="http://www.sourcetreeapp.com" target="_blank">Atlassian SourceTree</a> is the free Git client for Windows or Mac. '
        ].join('');

        return html;
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
        } else if (exercise.type === ExerciseType.MODELING && this.hasParticipations(exercise)) {
            const participation = exercise.participations[0];
            if (
                participation.initializationState === InitializationState.INITIALIZED ||
                participation.initializationState === InitializationState.FINISHED
            ) {
                return ParticipationStatus.MODELING_EXERCISE;
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

    @HostListener('document:click', ['$event'])
    clickOutside(event: any) {
        // If there's a last element-reference AND the click-event target is outside this element
        if (
            this.lastPopoverRef &&
            (this.lastPopoverRef as any)._elementRef.nativeElement.contains(event.target) &&
            !(this.lastPopoverRef as any)._windowRef &&
            !(this.lastPopoverRef as any)._windowRef.location.nativeElement.contains(event.target)
        ) {
            this.lastPopoverRef.close();
            this.lastPopoverRef = null;
        }
    }

    setCurrentPopoverOpen(popReference: any) {
        // If there's a last element-reference AND the new reference is different
        if (this.lastPopoverRef && this.lastPopoverRef !== popReference) {
            this.lastPopoverRef.close();
        }
        // Registering new popover ref
        this.lastPopoverRef = popReference;
    }

    ngOnDestroy(): void {
        // Remove router event subscription
        this.routerSubscription.unsubscribe();
    }
}
