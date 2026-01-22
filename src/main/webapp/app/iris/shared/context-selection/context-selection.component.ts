import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faArrowLeft, faBook, faCode, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';

export type ContextType = 'course' | 'lecture' | 'exercise';
type ViewState = 'main' | 'lecture-selection' | 'exercise-selection';

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
        icon: faBook,
        titleKey: 'artemisApp.iris.chat.contextSelection.selectLecture',
        descriptionKey: 'artemisApp.iris.chat.contextSelection.selectLectureDescription',
    },
    {
        type: 'exercise',
        icon: faCode,
        titleKey: 'artemisApp.iris.chat.contextSelection.selectExercise',
        descriptionKey: 'artemisApp.iris.chat.contextSelection.selectExerciseDescription',
    },
];

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

    // Icons
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faBook = faBook;
    protected readonly faCode = faCode;

    // Inputs
    readonly courseId = input<number>();
    readonly options = input<ContextOption[]>(DEFAULT_OPTIONS); //?

    // Two-way bindings
    readonly selected = model<ContextType>('course');
    readonly selectedLecture = model<Lecture | undefined>(undefined);
    readonly selectedExercise = model<Exercise | undefined>(undefined);

    // Internal state
    readonly currentView = signal<ViewState>('main');
    readonly searchQuery = signal('');
    readonly lectures = signal<Lecture[]>([]);
    readonly exercises = signal<Exercise[]>([]);
    readonly isLoading = signal(false);

    readonly resolvedOptions = computed(() => this.options());

    readonly filteredLectures = computed(() => {
        const query = this.searchQuery().toLowerCase();
        if (!query) {
            return this.lectures();
        }
        return this.lectures().filter((lecture) => lecture.title?.toLowerCase().includes(query));
    });

    readonly filteredExercises = computed(() => {
        const query = this.searchQuery().toLowerCase();
        if (!query) {
            return this.exercises();
        }
        return this.exercises().filter((exercise) => exercise.title?.toLowerCase().includes(query));
    });

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.loadCourseData(courseId);
            }
        });
    }

    onOptionClick(type: ContextType): void {
        if (type === 'course') {
            this.selected.set(type);
            this.selectedLecture.set(undefined);
            this.selectedExercise.set(undefined);
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.chatService.switchTo(ChatServiceMode.COURSE, courseId);
            }
        } else if (type === 'lecture') {
            this.selected.set(type);
            this.currentView.set('lecture-selection');
            this.searchQuery.set('');
        } else if (type === 'exercise') {
            this.selected.set(type);
            this.currentView.set('exercise-selection');
            this.searchQuery.set('');
        }
    }

    selectLecture(lecture: Lecture): void {
        this.selectedLecture.set(lecture);
        this.selectedExercise.set(undefined); // Reset exercise selection
        this.currentView.set('main');
        if (lecture.id !== undefined) {
            this.chatService.switchTo(ChatServiceMode.LECTURE, lecture.id);
        }
    }

    selectExercise(exercise: Exercise): void {
        this.selectedExercise.set(exercise);
        this.selectedLecture.set(undefined); // Reset lecture selection
        this.currentView.set('main');
        if (exercise.id !== undefined) {
            const mode = exercise.type === ExerciseType.TEXT ? ChatServiceMode.TEXT_EXERCISE : ChatServiceMode.PROGRAMMING_EXERCISE;
            this.chatService.switchTo(mode, exercise.id);
        }
    }

    goBack(): void {
        this.currentView.set('main');
        this.searchQuery.set('');
    }

    onSearch(query: string): void {
        this.searchQuery.set(query);
    }

    private loadCourseData(courseId: number): void {
        // Try to get course from cache first
        const cachedCourse = this.courseStorageService.getCourse(courseId);

        if (cachedCourse?.lectures?.length || cachedCourse?.exercises?.length) {
            // Cache has lectures/exercises, use them
            this.lectures.set(cachedCourse.lectures ?? []);
            this.exercises.set(cachedCourse.exercises ?? []);
            return;
        }

        // Fallback: Load from server
        this.isLoading.set(true);
        this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(courseId).subscribe({
            next: (response) => {
                const course = response.body;
                if (course) {
                    this.lectures.set(course.lectures ?? []);
                    this.exercises.set(course.exercises ?? []);
                }
                this.isLoading.set(false);
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }
}
