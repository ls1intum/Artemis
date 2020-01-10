import { Component, HostBinding, Input, OnDestroy, OnInit } from '@angular/core';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType, getIcon, getIconTooltip, ParticipationStatus, participationStatus } from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { StudentParticipation } from 'app/entities/participation';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Subscription } from 'rxjs/Subscription';
import { Course } from 'app/entities/course';
import { WindowRef } from 'app/core/websocket/window.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { AccountService } from 'app/core/auth/account.service';

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
        const cachedParticipation = this.participationWebsocketService.getParticipationForExercise(this.exercise.id);
        if (cachedParticipation) {
            this.exercise.studentParticipations = [cachedParticipation];
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise.id === this.exercise.id) {
                this.exercise.studentParticipations =
                    this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0
                        ? this.exercise.studentParticipations.map(el => {
                              return el.id === changedParticipation.id ? changedParticipation : el;
                          })
                        : [changedParticipation];
                this.exercise.participationStatus = participationStatus(this.exercise);
            }
        });
        this.exercise.participationStatus = participationStatus(this.exercise);
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
