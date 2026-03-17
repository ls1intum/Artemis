import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { RippleModule } from 'primeng/ripple';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { faChalkboardUser, faCheck, faFolderOpen, faGraduationCap, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { catchError, filter, map, of, switchMap, tap } from 'rxjs';

interface ContextMenuItem extends MenuItem {
    faIcon?: IconDefinition;
    items?: ContextMenuItem[];
}

// Maps exercise types that have Iris chat integration to their ChatServiceMode.
// To add Iris support for a new exercise type, add a single entry here.
const EXERCISE_TYPE_TO_CHAT_MODE: Record<string, ChatServiceMode> = {
    [ExerciseType.TEXT]: ChatServiceMode.TEXT_EXERCISE,
    [ExerciseType.PROGRAMMING]: ChatServiceMode.PROGRAMMING_EXERCISE,
};

const CONTEXT_STORAGE_KEY_PREFIX = 'iris-context-';

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrls: ['./context-selection.component.scss'],
    imports: [ButtonModule, MenuModule, FaIconComponent, RippleModule, InputTextModule, IconFieldModule, InputIconModule, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextSelectionComponent {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly chatService = inject(IrisChatService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly currentMode = toSignal(this.chatService.currentChatMode(), { initialValue: undefined });
    private readonly currentEntityId = toSignal(this.chatService.currentRelatedEntityId(), { initialValue: undefined });

    readonly courseId = input<number>();
    readonly isLoading = signal(false);
    readonly lectures = signal<Lecture[]>([]);
    readonly exercises = signal<Exercise[]>([]);
    readonly courseName = signal<string>('');

    readonly menuLabel = signal<string>('');
    readonly menuIcon = signal<IconDefinition>(faGraduationCap);

    readonly searchTerm = signal<string>('');

    readonly supportedExercises = computed(() => this.exercises().filter((e) => e.type && e.type in EXERCISE_TYPE_TO_CHAT_MODE));

    readonly allItems = computed<ContextMenuItem[]>(() => {
        const courseId = this.courseId();
        const courseName = this.courseName();
        const lectures = this.lectures();
        const exercises = this.supportedExercises();
        const items: ContextMenuItem[] = [];

        if (courseName) {
            items.push(
                { separator: true },
                {
                    label: 'artemisApp.iris.contextSelection.courseGroup',
                    items: [
                        {
                            label: courseName,
                            faIcon: faGraduationCap,
                            command: () => {
                                this.saveContextToStorage(ChatServiceMode.COURSE, courseId);
                                if (courseId !== undefined) {
                                    this.chatService.switchTo(ChatServiceMode.COURSE, courseId, true);
                                }
                            },
                        },
                    ],
                },
            );
        }

        if (lectures.length > 0) {
            items.push(
                { separator: true },
                {
                    label: 'artemisApp.iris.contextSelection.lecturesGroup',
                    items: lectures.map((lecture) => ({
                        label: lecture.title,
                        faIcon: faChalkboardUser,
                        command: () => {
                            this.saveContextToStorage(ChatServiceMode.LECTURE, lecture.id);
                            if (lecture.id !== undefined) {
                                this.chatService.switchTo(ChatServiceMode.LECTURE, lecture.id, true);
                            }
                        },
                    })),
                },
            );
        }

        if (exercises.length > 0) {
            items.push(
                { separator: true },
                {
                    label: 'artemisApp.iris.contextSelection.exercisesGroup',
                    items: exercises.map((exercise) => ({
                        label: exercise.title,
                        faIcon: getIcon(exercise.type) as IconDefinition,
                        command: () => {
                            const mode = exercise.type ? EXERCISE_TYPE_TO_CHAT_MODE[exercise.type] : undefined;
                            if (exercise.id !== undefined && mode) {
                                this.saveContextToStorage(mode, exercise.id);
                                this.chatService.switchTo(mode, exercise.id, true);
                            }
                        },
                    })),
                },
            );
        }

        return items;
    });

    readonly filteredItems = computed<ContextMenuItem[]>(() => {
        const term = this.searchTerm().trim().toLowerCase();
        if (!term) return this.allItems();
        return this.allItems()
            .filter((item) => !item.separator)
            .map((group) => ({
                ...group,
                items: group.items?.filter((sub) => sub.label?.toLowerCase().includes(term)),
            }))
            .filter((group) => (group.items?.length ?? 0) > 0);
    });

    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faCheck = faCheck;

    constructor() {
        toObservable(this.courseId)
            .pipe(
                filter((id): id is number => id !== undefined),
                tap(() => this.isLoading.set(true)),
                switchMap((courseId) => {
                    const cached = this.courseStorageService.getCourse(courseId);
                    if (cached?.lectures?.length || cached?.exercises?.length) {
                        return of({
                            courseName: cached.title ?? '',
                            lectures: cached.lectures ?? [],
                            exercises: cached.exercises ?? [],
                        });
                    }
                    return this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(courseId).pipe(
                        map((r) => ({
                            courseName: r.body?.title ?? '',
                            lectures: r.body?.lectures ?? [],
                            exercises: r.body?.exercises ?? [],
                        })),
                        catchError(() => of({ courseName: '', lectures: [] as Lecture[], exercises: [] as Exercise[] })),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((data) => {
                this.courseName.set(data.courseName);
                this.lectures.set(data.lectures);
                this.exercises.set(data.exercises);
                this.isLoading.set(false);
            });

        effect(() => {
            const mode = this.currentMode();
            const entityId = this.currentEntityId();
            const courseName = this.courseName();
            const lectures = this.lectures();
            const exercises = this.supportedExercises();

            if (!courseName) return;

            if (mode === ChatServiceMode.LECTURE && entityId !== undefined) {
                const lecture = lectures.find((l) => l.id === entityId);
                if (lecture?.title) {
                    this.menuLabel.set(lecture.title);
                    this.menuIcon.set(faChalkboardUser);
                    return;
                }
            }

            if ((mode === ChatServiceMode.PROGRAMMING_EXERCISE || mode === ChatServiceMode.TEXT_EXERCISE) && entityId !== undefined) {
                const exercise = exercises.find((e) => e.id === entityId);
                if (exercise?.title) {
                    this.menuLabel.set(exercise.title);
                    this.menuIcon.set(getIcon(exercise.type) as IconDefinition);
                    return;
                }
            }

            this.menuLabel.set(courseName);
            this.menuIcon.set(faGraduationCap);
        });
    }

    private saveContextToStorage(mode: ChatServiceMode, entityId?: number): void {
        const courseId = this.courseId();
        if (courseId !== undefined) {
            sessionStorage.setItem(CONTEXT_STORAGE_KEY_PREFIX + courseId, JSON.stringify({ mode, entityId }));
        }
    }
}
