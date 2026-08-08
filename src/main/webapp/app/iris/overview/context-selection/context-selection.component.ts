import { ChangeDetectionStrategy, Component, DestroyRef, ViewEncapsulation, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { faChalkboardUser, faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { SelectModule } from 'primeng/select';
import { ChipModule } from 'primeng/chip';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

interface ContextOption {
    label: string;
    value: string;
    faIcon: IconDefinition;
    mode: ChatServiceMode;
    entityId: number;
}

interface ContextGroup {
    label: string;
    items: ContextOption[];
}

// Maps exercise types that have Iris chat integration to their ChatServiceMode.
// To add Iris support for a new exercise type, add a single entry here.
const EXERCISE_TYPE_TO_CHAT_MODE: Record<string, ChatServiceMode> = {
    [ExerciseType.TEXT]: ChatServiceMode.TEXT_EXERCISE,
    [ExerciseType.PROGRAMMING]: ChatServiceMode.PROGRAMMING_EXERCISE,
};

/** Icon for a selected context, derived from its mode alone so the chip does not need the entity loaded. */
function iconForMode(mode: ChatServiceMode): IconDefinition {
    if (mode === ChatServiceMode.LECTURE) {
        return faChalkboardUser;
    }
    const exerciseType = Object.keys(EXERCISE_TYPE_TO_CHAT_MODE).find((type) => EXERCISE_TYPE_TO_CHAT_MODE[type] === mode);
    return getIcon(exerciseType as ExerciseType) as IconDefinition;
}

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrls: ['./context-selection.component.scss'],
    imports: [SelectModule, ChipModule, TooltipModule, FormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class ContextSelectionComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly lectureService = inject(LectureService);
    private readonly courseOverviewExercisesService = inject(CourseOverviewExercisesService);
    private readonly entityTitleService = inject(EntityTitleService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;

    readonly disabled = input<boolean>(false);
    readonly contextChanged = output<void>();

    readonly courseId = signal<number | undefined>(this.chatService.getCourseId());

    /**
     * The selectable lectures and exercises. Loaded when the dropdown is first opened rather than up front: the Iris
     * chat is embedded on lecture and exercise pages where the picker is often never used, and these options used to be
     * read off a course object that the course overview happened to have loaded — which silently produced an empty
     * picker as soon as the overview stopped loading that content.
     */
    private readonly lecturesSignal = signal<Lecture[]>([]);
    private readonly exercisesSignal = signal<Exercise[]>([]);
    private optionsLoadedForCourseId?: number;

    readonly lectures = this.lecturesSignal.asReadonly();
    readonly exercises = this.exercisesSignal.asReadonly();

    /** Resolved name of the currently selected context; the page can set a context without carrying its name. */
    private readonly activeContextName = signal<string>('');

    constructor() {
        effect(() => {
            const context = this.chatService.displayContext();
            if (!context || context.mode === ChatServiceMode.COURSE) {
                this.activeContextName.set('');
                return;
            }
            if (context.entityName) {
                this.activeContextName.set(context.entityName);
                return;
            }
            const entityType = context.mode === ChatServiceMode.LECTURE ? EntityType.LECTURE : EntityType.EXERCISE;
            this.entityTitleService
                .getTitle(entityType, [context.entityId])
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((title) => this.activeContextName.set(title));
        });
    }

    /**
     * Loads the pickable lectures and exercises for the course, once per course. Called when the dropdown opens.
     */
    loadContextOptions(): void {
        const courseId = this.courseId();
        if (courseId === undefined || this.optionsLoadedForCourseId === courseId) {
            return;
        }
        this.optionsLoadedForCourseId = courseId;
        this.lectureService
            .findAllByCourseIdForOverview(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: (res) => this.lecturesSignal.set(res.body ?? []), error: () => this.lecturesSignal.set([]) });
        this.courseOverviewExercisesService
            .loadIfNeeded(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: (data) => this.exercisesSignal.set(data.exercises ?? []), error: () => this.exercisesSignal.set([]) });
    }

    readonly supportedExercises = computed(() => this.exercises().filter((e) => e.type && e.type in EXERCISE_TYPE_TO_CHAT_MODE));

    readonly selectedValue = computed(() => {
        const ctx = this.chatService.displayContext();
        if (ctx === undefined) return undefined;
        return `${ctx.mode}:${ctx.entityId}`;
    });

    readonly allGroups = computed<ContextGroup[]>(() => {
        const lectures = this.lectures();
        const exercises = this.supportedExercises();
        const groups: ContextGroup[] = [];

        if (lectures.length > 0) {
            groups.push({
                label: 'artemisApp.iris.contextSelection.lecturesGroup',
                items: lectures
                    .filter((l) => l.id !== undefined)
                    .map((lecture) => ({
                        label: lecture.title ?? '',
                        value: `${ChatServiceMode.LECTURE}:${lecture.id}`,
                        faIcon: faChalkboardUser,
                        mode: ChatServiceMode.LECTURE,
                        entityId: lecture.id!,
                    })),
            });
        }

        if (exercises.length > 0) {
            groups.push({
                label: 'artemisApp.iris.contextSelection.exercisesGroup',
                items: exercises
                    .filter((e) => e.id !== undefined)
                    .map((exercise) => ({
                        label: exercise.title ?? '',
                        value: `${EXERCISE_TYPE_TO_CHAT_MODE[exercise.type!]}:${exercise.id}`,
                        faIcon: getIcon(exercise.type) as IconDefinition,
                        mode: EXERCISE_TYPE_TO_CHAT_MODE[exercise.type!],
                        entityId: exercise.id!,
                    })),
            });
        }

        return groups;
    });

    readonly activeChip = computed<ContextOption | undefined>(() => {
        const context = this.chatService.displayContext();
        if (context === undefined || context.mode === ChatServiceMode.COURSE) {
            return undefined;
        }
        // Built from the context itself rather than by searching the picker options, so the chip renders correctly even
        // when the options have never been loaded (the picker is lazy, and a page can set a context on its own).
        return {
            label: this.activeContextName(),
            value: `${context.mode}:${context.entityId}`,
            faIcon: iconForMode(context.mode),
            mode: context.mode,
            entityId: context.entityId,
        };
    });

    onSelectionChange(value: string): void {
        const option = this.allGroups()
            .flatMap((g) => g.items)
            .find((o) => o.value === value);
        if (option) {
            this.chatService.stagePendingContext(option.mode, option.entityId, option.label);
            this.contextChanged.emit();
        }
    }

    onChipRemove(): void {
        const courseId = this.courseId();
        if (courseId !== undefined) {
            this.chatService.stagePendingContext(ChatServiceMode.COURSE, courseId);
            this.contextChanged.emit();
        }
    }
}
