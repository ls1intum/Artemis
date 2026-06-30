import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisHandoffContextService } from 'app/iris/overview/services/iris-handoff-context.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';
import { filter, switchMap, take } from 'rxjs';

const EXERCISE_CHAT_MODES: Partial<Record<ExerciseType, ChatServiceMode>> = {
    [ExerciseType.TEXT]: ChatServiceMode.TEXT_EXERCISE,
    [ExerciseType.PROGRAMMING]: ChatServiceMode.PROGRAMMING_EXERCISE,
};

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseChatbotComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly irisHandoffContextService = inject(IrisHandoffContextService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    readonly courseId = input<number>();
    /** Optional context type from handoff: 'lecture' or 'exercise'. */
    readonly initialContextType = input<string | undefined>(undefined);
    /** Entity ID corresponding to initialContextType. */
    readonly initialContextEntityId = input<number | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId === undefined) return;

            this.chatService.setCourseId(courseId);

            const contextType = this.initialContextType();
            const entityId = this.initialContextEntityId();

            // Track version as a reactive dependency so the effect re-runs on every Continue
            // click, even when the lecture/exercise context is the same as the previous click.
            this.irisHandoffContextService.version();

            // Read context without tracking so clearing it does not re-trigger this effect.
            const handoffCtx = untracked(() => this.irisHandoffContextService.context());

            if (handoffCtx) {
                untracked(() => {
                    this.irisHandoffContextService.clear();

                    // Use the routing target stored in the handoff context (captured at click time)
                    // rather than the URL-derived contextType/entityId signals.  This prevents a race
                    // where the effect fires before Angular has settled the route signals after
                    // navigation, which would create a session in the wrong mode and then trigger a
                    // switchTo() that reloads the previous session.
                    const target = handoffCtx.target;
                    if (target.type === 'lecture' && target.lectureId !== undefined) {
                        this.chatService.switchToNewSession(ChatServiceMode.LECTURE, target.lectureId);
                    } else if (target.type === 'exercise' && target.exerciseId !== undefined) {
                        const mode = this.resolveExerciseMode(courseId, target.exerciseId);
                        this.chatService.switchToNewSession(mode ?? ChatServiceMode.COURSE, mode ? target.exerciseId : courseId);
                    } else {
                        this.chatService.switchToNewSession(ChatServiceMode.COURSE, courseId);
                    }

                    // Wait for the new session to finish loading, then seed it with the Q&A.
                    this.chatService.initialLoadComplete$
                        .pipe(
                            filter(Boolean),
                            take(1),
                            switchMap(() => this.chatService.seedFromGlobalSearch(handoffCtx.query, handoffCtx.answer)),
                            takeUntilDestroyed(this.destroyRef),
                        )
                        .subscribe();
                });
            } else {
                if (contextType === 'lecture' && entityId !== undefined) {
                    this.chatService.switchTo(ChatServiceMode.LECTURE, entityId);
                } else if (contextType === 'exercise' && entityId !== undefined) {
                    const mode = this.resolveExerciseMode(courseId, entityId);
                    this.chatService.switchTo(mode ?? ChatServiceMode.COURSE, mode ? entityId : courseId);
                } else {
                    this.chatService.switchTo(ChatServiceMode.COURSE, courseId);
                }
            }
        });
    }

    public toggleChatHistory(): void {
        const baseChatbot = this.irisBaseChatbot();
        if (!baseChatbot) {
            return;
        }
        baseChatbot.setChatHistoryVisibility(!baseChatbot.isChatHistoryOpen());
    }

    private resolveExerciseMode(courseId: number, exerciseId: number): ChatServiceMode | undefined {
        const exercise = this.courseStorageService.getCourse(courseId)?.exercises?.find((e) => e.id === exerciseId);
        return exercise?.type ? EXERCISE_CHAT_MODES[exercise.type] : undefined;
    }
}
