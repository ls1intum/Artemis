import { Component, HostListener, Input, OnInit, Pipe, PipeTransform } from '@angular/core';
import { Course, CourseExerciseService, CourseService } from '../../entities/course';
import { Exercise, ExerciseType } from '../../entities/exercise';
import { JhiWebsocketService, Principal } from '../../shared';
import { WindowRef } from '../../shared/websocket/window.service';
import { RepositoryService } from '../../entities/repository/repository.service';
import { ResultService } from '../../entities/result';
import { NgbModal, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { ExerciseParticipationService, Participation, ParticipationService } from '../../entities/participation';
import * as moment from 'moment';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from '../../app.constants';

@Pipe({ name: 'showExercise' })
export class ShowExercisePipe implements PipeTransform {
    transform(allExercises: Exercise[], showInactiveExercises: boolean) {
        return allExercises.filter(exercise => showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || Date.parse(exercise.dueDate) >= Date.now());
    }
}

@Component({
    selector: 'jhi-exercise-list',
    templateUrl: './exercise-list.component.html',
    providers: [
                    JhiAlertService,
                    WindowRef,
                    ResultService,
                    RepositoryService,
                    CourseExerciseService,
                    ParticipationService,
                    ExerciseParticipationService,
                    NgbModal
                ]
})

export class ExerciseListComponent implements OnInit {

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    _course: Course;

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
            this.courseExerciseService.query(course.id, {
                courseId: course.id,
                withLtiOutcomeUrlExisting: true
            }).subscribe(exercises => {
                this.initExercises(exercises);
            });
        }
    }
    @Input() filterByExerciseId;

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

    constructor(private jhiWebsocketService: JhiWebsocketService,
                private jhiAlertService: JhiAlertService,
                private $window: WindowRef,
                private principal: Principal,
                private resultService: ResultService,
                private repositoryService: RepositoryService,
                private courseExerciseService: CourseExerciseService,
                private participationService: ParticipationService,
                private exerciseParticipationService: ExerciseParticipationService,
                private httpClient: HttpClient,
                private courseService: CourseService,
                public modalService: NgbModal,
                private router: Router) {
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
    }

    initExercises(exercises) {
        if (this.filterByExerciseId) {
            exercises = exercises.filter(exercise => exercise.id === this.filterByExerciseId);
        }

        this.numOfInactiveExercises = exercises.filter(exercise => !this.showExercise(exercise)).length;

        for (const exercise of exercises) {
            if (!exercise.participation) {
                this.exerciseParticipationService.find(this.course.id, exercise.id).subscribe(participation => {
                    exercise.participation = participation;
                    exercise.participationStatus = this.participationStatus(exercise);
                    if (exercise.type === ExerciseType.QUIZ) {
                        exercise.isActiveQuiz = this.isActiveQuiz(exercise);
                    }
                });
            }

            exercise.participationStatus = this.participationStatus(exercise);

            // If the User is a student: subscribe the release Websocket of every quizExercise
            if (exercise.type === ExerciseType.QUIZ) {
                exercise.isActiveQuiz = this.isActiveQuiz(exercise);

                exercise.isPracticeModeAvailable = exercise.isPlannedToStart &&
                    exercise.isOpenForPractice &&
                    moment(exercise.dueDate).isBefore(moment());

                exercise.isAtLeastTutor = this.principal.hasGroup(this.course.instructorGroupName) ||
                    this.principal.hasGroup(this.course.teachingAssistantGroupName) ||
                    this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN']);
            }
        }
        exercises.sort((a: Exercise, b: Exercise) => {
            return +new Date(a.dueDate) - +new Date(b.dueDate);
        });
        this.exercises = exercises;
    }

    isActiveQuiz(exercise) {
        return exercise.participationStatus === 'quiz-uninitialized' ||
            exercise.participationStatus === 'quiz-active' ||
            exercise.participationStatus === 'quiz-submitted';
    }

    showExercise(exercise: Exercise) {
        return this.showInactiveExercises === true || exercise.type === ExerciseType.QUIZ || Date.parse(exercise.dueDate) >= Date.now();
    }

    getRepositoryPassword() {
        this.httpClient.get(`${SERVER_API_URL}/api/account/password`).subscribe(res => {
            const password = res['password'];
            if (password) {
                this.repositoryPassword = password;
            }
        });
    }

    start(exercise: Exercise) {
        exercise.loading = true;

        if (exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/quiz', exercise.id]);
        }

        this.courseExerciseService.start(this.course.id, exercise.id).finally(() => exercise.loading = false)
            .subscribe(data => {
                    if (data) {
                        exercise['participation'] = data.participation;
                        exercise.participationStatus = this.participationStatus(exercise);
                    }
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        this.jhiAlertService.success('arTeMiSApp.exercise.personalRepository');
                    }
                }, error => {
                    console.log(error);
                    this.jhiAlertService.warning('arTeMiSApp.exercise.startError');
                }
            );
    }

    resume(exercise: Exercise) {
        exercise.loading = true;
        this.courseExerciseService.resume(this.course.id, exercise.id).finally(() => exercise.loading = false)
            .subscribe(
                () => true, error => {
                    console.log(error.status + ' ' + error.message);
                });
    }

    startPractice(exercise: Exercise) {
        return this.router.navigate(['/quiz', exercise.id, 'practice']);
    }

    toggleshowInactiveExercises() {
        this.showInactiveExercises = !this.showInactiveExercises;
    }

    buildSourceTreeUrl(cloneUrl): string {
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
            '<pre style="max-width: 550px;">', exercise['participation'].repositoryUrl, '</pre>',
            this.repositoryPassword ? '<p>Your password is: <code class="password">' + this.repositoryPassword + '</code> (hover to show)<p>' : '',
            '<a class="btn btn-primary btn-sm" href="', this.buildSourceTreeUrl(exercise['participation'].repositoryUrl), '">Clone in SourceTree</a>',
            ' <a href="http://www.sourcetreeapp.com" target="_blank">Atlassian SourceTree</a> is the free Git client for Windows or Mac. '
        ].join('');

        return html;
    }

    participationStatus(exercise): string {
        if (exercise.type === ExerciseType.QUIZ) {
            if ((!exercise.isPlannedToStart || moment(exercise.releaseDate).isAfter(moment())) && exercise.visibleToStudents) {
                return 'quiz-not-started';
            } else if (Object.keys(exercise.participation).length === 0 &&
                (!exercise.isPlannedToStart || moment(exercise.dueDate).isAfter(moment())) && exercise.visibleToStudents) {
                return 'quiz-uninitialized';
            } else if (Object.keys(exercise.participation).length === 0) {
                return 'quiz-not-participated';
            } else if (exercise.participation.initializationState === 'INITIALIZED' && moment(exercise.dueDate).isAfter(moment())) {
                return 'quiz-active';
            } else if (exercise.participation.initializationState === 'FINISHED' && moment(exercise.dueDate).isAfter(moment())) {
                return 'quiz-submitted';
            } else {
                if (exercise.participation.results.length === 0) {
                    return 'quiz-not-participated';
                }
                return 'quiz-finished';
            }
        } else if (exercise.type === ExerciseType.MODELING && exercise.participation) {
            if (exercise.participation.initializationState === 'INITIALIZED' || exercise.participation.initializationState === 'FINISHED') {
                return 'modeling-exercise';
            }
        }
        if (exercise.participation == null || Object.keys(exercise.participation).length === 0) {
            return 'uninitialized';
        } else if (exercise.participation.initializationState === 'INITIALIZED') {
            return 'initialized';
        }
        return 'inactive';
    }

    @HostListener('document:click', ['$event'])
    clickOutside(event) {
        // If there's a last element-reference AND the click-event target is outside this element
        if (this.lastPopoverRef && !(this.lastPopoverRef as any)._elementRef.nativeElement.contains(event.target) &&
            !(this.lastPopoverRef as any)._windowRef != null &&
            !(this.lastPopoverRef as any)._windowRef.location.nativeElement.contains(event.target)) {
            this.lastPopoverRef.close();
            this.lastPopoverRef = null;
        }
    }

    setCurrentPopoverOpen(popReference) {
        // If there's a last element-reference AND the new reference is different
        if (this.lastPopoverRef && this.lastPopoverRef !== popReference) {
            this.lastPopoverRef.close();
        }
        // Registering new popover ref
        this.lastPopoverRef = popReference;
    }
}
