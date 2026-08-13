import { Component, DestroyRef, HostBinding, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation, isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
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
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { deepClone } from 'app/foundation/util/deep-clone.util';

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
    readonly exercise = input.required<Exercise>();
    readonly course = input.required<Course>();
    /**
     * PresentationMode deactivates the interactivity of the component
     */
    readonly isPresentationMode = input(false);

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    // Signal-based state
    private readonly _enrichedExercise = signal<Exercise | undefined>(undefined);
    private readonly _exerciseCategories = signal<ExerciseCategory[]>([]);
    private readonly _isAfterAssessmentDueDate = signal(false);
    private readonly _dueDate = signal<dayjs.Dayjs | undefined>(undefined);
    private readonly _gradedStudentParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _studentParticipations = signal<StudentParticipation[]>([]);

    // Public read-only accessors - enrichedExercise provides the exercise with role checks applied
    readonly enrichedExercise = computed(() => this._enrichedExercise() ?? this.exercise());
    readonly exerciseCategories = this._exerciseCategories.asReadonly();
    readonly isAfterAssessmentDueDate = this._isAfterAssessmentDueDate.asReadonly();
    readonly dueDate = this._dueDate.asReadonly();
    readonly gradedStudentParticipation = this._gradedStudentParticipation.asReadonly();
    readonly studentParticipations = this._studentParticipations.asReadonly();

    readonly routerLink = computed(() => {
        const course = this.course();
        const exercise = this.exercise();
        if (exercise?.type === ExerciseType.QUIZ) {
            const participation = this.gradedStudentParticipation();
            if (isPracticeMode(participation)) {
                return ['/courses', course?.id?.toString() ?? '', 'exercises', 'quiz-exercises', exercise?.id?.toString() ?? '', 'practice', participation!.id!.toString()];
            }
            return ['/courses', course?.id?.toString() ?? '', 'exercises', 'quiz-exercises', exercise?.id?.toString() ?? '', 'live'];
        }
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
            .subscribe((changedParticipation: Participation | undefined) => {
                const exerciseValue = this.exercise();
                if (changedParticipation && exerciseValue?.id && changedParticipation.exercise?.id === exerciseValue.id) {
                    const studentParticipation = changedParticipation as StudentParticipation;
                    const currentParticipations = this._studentParticipations();
                    const updatedParticipations = currentParticipations.length
                        ? currentParticipations.map((el) => (el.id === studentParticipation.id ? studentParticipation : el))
                        : [studentParticipation];
                    this._studentParticipations.set(updatedParticipations);
                    const participation = this.participationService.getSpecificStudentParticipation(updatedParticipations, false);
                    this._gradedStudentParticipation.set(participation);
                    this._dueDate.set(getExerciseDueDate(exerciseValue, participation));
                    // Re-enrich from the input exercise rather than copying the enriched one. The enriched exercise
                    // carries `course`, and the stored course reaches every other exercise of the course, so copying it
                    // would clone the whole course graph on each websocket event — once per row of the overview — and
                    // leave this row holding a course detached from the one the rest of the page shares.
                    this._enrichedExercise.set(this.enrich(exerciseValue, this.course(), updatedParticipations));
                }
            });
    }

    /**
     * Builds the row's own copy of the exercise, carrying the role checks, the course and the given participations.
     *
     * The copy exists so the input signal's object is never mutated. It is taken from `exercise` — the input, whose
     * `course` the server omits (`@JsonIgnoreProperties("course")` on `Course.exercises`) — and the course is attached
     * afterwards, by reference. That order is what keeps the copy cheap: cloning an exercise that already carries the
     * course would copy the whole course graph, since the course reaches every other exercise of the course.
     *
     * `studentParticipations` is only passed when a websocket update supersedes them; omitting it keeps the ones the
     * input exercise came with.
     */
    private enrich(exercise: Exercise, course: Course, studentParticipations?: StudentParticipation[]): Exercise {
        const courseForRoleCheck = course || exercise.exerciseGroup?.exam?.course;
        const enrichedExercise: Exercise = deepClone(exercise);
        enrichedExercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(courseForRoleCheck);
        enrichedExercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(courseForRoleCheck);
        enrichedExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(courseForRoleCheck);
        enrichedExercise.course = course;
        if (studentParticipations) {
            enrichedExercise.studentParticipations = studentParticipations;
        }

        if (enrichedExercise.type === ExerciseType.QUIZ) {
            const quizExercise = enrichedExercise as QuizExercise;
            quizExercise.isActiveQuiz = this.exerciseService.isActiveQuiz(quizExercise);
            quizExercise.isPracticeModeAvailable = quizExercise.quizEnded;
        }

        return enrichedExercise;
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

        this._enrichedExercise.set(this.enrich(exercise, course));
        this._isAfterAssessmentDueDate.set(!exercise.assessmentDueDate || dayjs().isAfter(exercise.assessmentDueDate));
        this._exerciseCategories.set(exercise.categories || []);
    }

    getUrgentClass(date?: dayjs.Dayjs) {
        if (!date) {
            return undefined;
        }
        const remainingDays = date.diff(dayjs(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        }
        return undefined;
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise;
    }
}
