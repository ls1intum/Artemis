import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input, signal } from '@angular/core';
import { faChalkboardUser, faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
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

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrls: ['./context-selection.component.scss'],
    imports: [SelectModule, ChipModule, TooltipModule, FormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class ContextSelectionComponent {
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly chatService = inject(IrisChatService);

    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;

    readonly disabled = input<boolean>(false);

    readonly courseId = signal<number | undefined>(this.chatService.getCourseId());

    readonly courseName = computed<string>(() => {
        const courseId = this.courseId();
        return courseId !== undefined ? (this.courseStorageService.getCourse(courseId)?.title ?? '') : '';
    });
    readonly lectures = computed<Lecture[]>(() => {
        const courseId = this.courseId();
        return courseId !== undefined ? (this.courseStorageService.getCourse(courseId)?.lectures ?? []) : [];
    });
    readonly exercises = computed<Exercise[]>(() => {
        const courseId = this.courseId();
        return courseId !== undefined ? (this.courseStorageService.getCourse(courseId)?.exercises ?? []) : [];
    });

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
        const mode = this.chatService.displayContext()?.mode;
        const entityId = this.chatService.displayContext()?.entityId;
        if (mode === undefined || entityId === undefined || mode === ChatServiceMode.COURSE) {
            return undefined;
        }
        return this.allGroups()
            .flatMap((g) => g.items)
            .find((o) => o.mode === mode && o.entityId === entityId);
    });

    onSelectionChange(value: string): void {
        const option = this.allGroups()
            .flatMap((g) => g.items)
            .find((o) => o.value === value);
        if (option) {
            this.chatService.switchContextOfCurrentSession(option.mode, option.entityId, option.label);
        }
    }

    onChipRemove(): void {
        const courseId = this.courseId();
        if (courseId !== undefined) {
            this.chatService.switchContextOfCurrentSession(ChatServiceMode.COURSE, courseId);
        }
    }
}
