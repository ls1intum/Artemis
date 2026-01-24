import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faArrowLeft, faChalkboardUser, faGraduationCap, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { catchError, filter, map, of, switchMap, tap } from 'rxjs';

export type ContextType = 'course' | 'lecture' | 'exercise';
type ViewState = 'main' | 'lecture-selection' | 'exercise-selection';

// Discriminated union for selection state
export type Selection = { type: 'course' } | { type: 'lecture'; lecture: Lecture } | { type: 'exercise'; exercise: Exercise };

export interface ContextOption {
    type: ContextType;
    icon: IconDefinition;
    titleKey: string;
    descriptionKey: string;
}

const DEFAULT_OPTIONS: ContextOption[] = [
    {
        type: 'course',
        icon: faGraduationCap,
        titleKey: 'artemisApp.iris.chat.contextSelection.entireCourse',
        descriptionKey: 'artemisApp.iris.chat.contextSelection.entireCourseDescription',
    },
    {
        type: 'lecture',
        icon: faChalkboardUser,
        titleKey: 'artemisApp.iris.chat.contextSelection.selectLecture',
        descriptionKey: 'artemisApp.iris.chat.contextSelection.selectLectureDescription',
    },
    {
        type: 'exercise',
        icon: faListAlt,
        titleKey: 'artemisApp.iris.chat.contextSelection.selectExercise',
        descriptionKey: 'artemisApp.iris.chat.contextSelection.selectExerciseDescription',
    },
];

// Supported exercise types for Iris chat
const SUPPORTED_EXERCISE_TYPES = [ExerciseType.TEXT, ExerciseType.PROGRAMMING];

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrl: './context-selection.component.scss',
    imports: [FaIconComponent, TranslateDirective, SearchFilterComponent, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextSelectionComponent {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly chatService = inject(IrisChatService);
    private readonly destroyRef = inject(DestroyRef);

    // Icons
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faChalkboardUser = faChalkboardUser;

    // Inputs
    readonly courseId = input<number>();
    readonly options = input<ContextOption[]>(DEFAULT_OPTIONS);

    // Consolidated selection state (discriminated union)
    readonly selection = signal<Selection>({ type: 'course' });

    // UI state
    readonly currentView = signal<ViewState>('main');
    readonly searchQuery = signal('');
    readonly lectures = signal<Lecture[]>([]);
    readonly exercises = signal<Exercise[]>([]);
    readonly isLoading = signal(false);

    // Derived state from selection
    readonly selectedType = computed(() => this.selection().type);

    readonly selectedLecture = computed(() => {
        const sel = this.selection();
        return sel.type === 'lecture' ? sel.lecture : undefined;
    });

    readonly selectedExercise = computed(() => {
        const sel = this.selection();
        return sel.type === 'exercise' ? sel.exercise : undefined;
    });

    readonly filteredLectures = computed(() => {
        const query = this.searchQuery().toLowerCase();
        if (!query) {
            return this.lectures();
        }
        return this.lectures().filter((lecture) => lecture.title?.toLowerCase().includes(query));
    });

    readonly filteredExercises = computed(() => {
        const query = this.searchQuery().toLowerCase();
        const supportedExercises = this.exercises().filter((exercise) => exercise.type && SUPPORTED_EXERCISE_TYPES.includes(exercise.type));
        if (!query) {
            return supportedExercises;
        }
        return supportedExercises.filter((exercise) => exercise.title?.toLowerCase().includes(query));
    });

    constructor() {
        toObservable(this.courseId)
            .pipe(
                filter((courseId): courseId is number => courseId !== undefined),
                tap(() => this.isLoading.set(true)),
                switchMap((courseId) => {
                    // Try cache first
                    const cachedCourse = this.courseStorageService.getCourse(courseId);
                    if (cachedCourse?.lectures?.length || cachedCourse?.exercises?.length) {
                        return of({ lectures: cachedCourse.lectures ?? [], exercises: cachedCourse.exercises ?? [] });
                    }
                    // Fallback: Load from server
                    return this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(courseId).pipe(
                        map((response) => ({
                            lectures: response.body?.lectures ?? [],
                            exercises: response.body?.exercises ?? [],
                        })),
                        catchError(() => of({ lectures: [] as Lecture[], exercises: [] as Exercise[] })),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((data) => {
                this.lectures.set(data.lectures);
                this.exercises.set(data.exercises);
                this.isLoading.set(false);
            });
    }

    onOptionClick(type: ContextType): void {
        if (type === 'course') {
            this.selection.set({ type: 'course' });
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.chatService.switchTo(ChatServiceMode.COURSE, courseId, true);
            }
        } else if (type === 'lecture') {
            this.currentView.set('lecture-selection');
            this.searchQuery.set('');
        } else if (type === 'exercise') {
            this.currentView.set('exercise-selection');
            this.searchQuery.set('');
        }
    }

    selectLecture(lecture: Lecture): void {
        this.selection.set({ type: 'lecture', lecture });
        this.currentView.set('main');
        if (lecture.id !== undefined) {
            this.chatService.switchTo(ChatServiceMode.LECTURE, lecture.id, true);
        }
    }

    selectExercise(exercise: Exercise): void {
        this.selection.set({ type: 'exercise', exercise });
        this.currentView.set('main');
        if (exercise.id !== undefined) {
            const mode = exercise.type === ExerciseType.TEXT ? ChatServiceMode.TEXT_EXERCISE : ChatServiceMode.PROGRAMMING_EXERCISE;
            this.chatService.switchTo(mode, exercise.id, true);
        }
    }

    goBack(): void {
        this.currentView.set('main');
        this.searchQuery.set('');
    }

    onSearch(query: string): void {
        this.searchQuery.set(query);
    }

    getExerciseIcon(exercise: Exercise): IconDefinition {
        return getIcon(exercise.type) as IconDefinition;
    }
}
