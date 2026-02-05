import { Component, DestroyRef, HostBinding, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SubmissionResultStatusComponent } from '../../submission-result-status/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from '../../exercise-details/student-actions/exercise-details-student-actions.component';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-course-exercise-row',
    templateUrl: './course-exercise-row.component.html',
    styleUrls: ['./course-exercise-row.scss'],
    imports: [
        RouterLink,
        FaIconComponent,
        NgbTooltip,
        SubmissionResultStatusComponent,
        ExerciseDetailsStudentActionsComponent,
        NgClass,
        ExerciseCategoriesComponent,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class CourseExerciseRowComponent implements OnInit {
    private accountService = inject(AccountService);
    private participationService = inject(ParticipationService);
    private exerciseService = inject(ExerciseService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private destroyRef = inject(DestroyRef);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;
    @HostBinding('class') classes = 'exercise-row';
    readonly exercise = input<Exercise>(undefined!);
    readonly course = input<Course>(undefined!);
    /**
     * PresentationMode deactivates the interactivity of the component
     */
    readonly isPresentationMode = input(false);

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    // Signal-based state
    private readonly _exerciseCategories = signal<ExerciseCategory[]>([]);
    private readonly _isAfterAssessmentDueDate = signal(false);
    private readonly _dueDate = signal<dayjs.Dayjs | undefined>(undefined);
    private readonly _gradedStudentParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _studentParticipations = signal<StudentParticipation[]>([]);

    // Public computed accessors
    readonly exerciseCategories = computed(() => this._exerciseCategories());
    readonly isAfterAssessmentDueDate = computed(() => this._isAfterAssessmentDueDate());
    readonly dueDate = computed(() => this._dueDate());
    readonly gradedStudentParticipation = computed(() => this._gradedStudentParticipation());
    readonly studentParticipations = computed(() => this._studentParticipations());

    readonly routerLink = computed(() => {
        const course = this.course();
        const exercise = this.exercise();
        return ['/courses', course?.id?.toString() ?? '', 'exercises', exercise?.id?.toString() ?? ''];
    });

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            const course = this.course();
            untracked(() => {
                this.updateExerciseData(exercise, course);
            });
        });
    }

    ngOnInit() {
        const exercise = this.exercise();
        if (exercise?.studentParticipations?.length) {
            this._studentParticipations.set(exercise.studentParticipations);
            this._gradedStudentParticipation.set(this.participationService.getSpecificStudentParticipation(exercise.studentParticipations, false));
        }

        this.participationWebsocketService
            .subscribeForParticipationChanges()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((changedParticipation: StudentParticipation) => {
                const exerciseValue = this.exercise();
                if (changedParticipation && exerciseValue?.id && changedParticipation.exercise?.id === exerciseValue.id) {
                    const currentParticipations = this._studentParticipations();
                    const updatedParticipations = currentParticipations.length
                        ? currentParticipations.map((el) => (el.id === changedParticipation.id ? changedParticipation : el))
                        : [changedParticipation];
                    this._studentParticipations.set(updatedParticipations);
                    const participation = this.participationService.getSpecificStudentParticipation(updatedParticipations, false);
                    this._gradedStudentParticipation.set(participation);
                    this._dueDate.set(getExerciseDueDate(exerciseValue, participation));
                }
            });
    }

    private updateExerciseData(exercise: Exercise, course: Course): void {
        if (!exercise) {
            return;
        }
        const cachedParticipations = this.participationWebsocketService.getParticipationsForExercise(exercise.id!);
        if (cachedParticipations?.length) {
            this._studentParticipations.set(cachedParticipations);
            this._gradedStudentParticipation.set(this.participationService.getSpecificStudentParticipation(cachedParticipations, false));
        }
        this._dueDate.set(getExerciseDueDate(exercise, this._gradedStudentParticipation()));

        // Restore role checks that downstream components depend on
        const courseForRoleCheck = course || exercise.exerciseGroup?.exam?.course;
        exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(courseForRoleCheck);
        exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(courseForRoleCheck);
        exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(courseForRoleCheck);

        this._isAfterAssessmentDueDate.set(!exercise.assessmentDueDate || dayjs().isAfter(exercise.assessmentDueDate));

        // Restore quiz-specific setup
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            quizExercise.isActiveQuiz = this.exerciseService.isActiveQuiz(quizExercise);
            quizExercise.isPracticeModeAvailable = quizExercise.quizEnded;
        }

        this._exerciseCategories.set(exercise.categories || []);
        exercise.course = course;
    }

    getUrgentClass(date?: dayjs.Dayjs) {
        if (!date) {
            return undefined;
        }
        const remainingDays = date.diff(dayjs(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        }
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
