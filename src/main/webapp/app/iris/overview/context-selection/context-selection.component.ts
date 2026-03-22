import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faChalkboardUser, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { catchError, filter, map, of, switchMap, tap } from 'rxjs';
import { SelectModule } from 'primeng/select';
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

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrls: ['./context-selection.component.scss'],
    imports: [SelectModule, FormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
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

    readonly supportedExercises = computed(() => this.exercises().filter((e) => e.type && e.type in EXERCISE_TYPE_TO_CHAT_MODE));

    readonly selectedValue = computed(() => {
        const mode = this.currentMode();
        const entityId = this.currentEntityId();
        if (mode === undefined || entityId === undefined) return undefined;
        return `${mode}:${entityId}`;
    });

    readonly allGroups = computed<ContextGroup[]>(() => {
        const courseId = this.courseId();
        const courseName = this.courseName();
        const lectures = this.lectures();
        const exercises = this.supportedExercises();
        const groups: ContextGroup[] = [];

        if (courseName && courseId !== undefined) {
            groups.push({
                label: 'artemisApp.iris.contextSelection.courseGroup',
                items: [
                    {
                        label: courseName,
                        value: `${ChatServiceMode.COURSE}:${courseId}`,
                        faIcon: faGraduationCap,
                        mode: ChatServiceMode.COURSE,
                        entityId: courseId,
                    },
                ],
            });
        }

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
    }

    onSelectionChange(value: string): void {
        const option = this.allGroups()
            .flatMap((g) => g.items)
            .find((o) => o.value === value);
        if (option) {
            this.chatService.createNewChat(option.mode, option.entityId);
        }
    }
}
