import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { of } from 'rxjs';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';
import { SidebarView } from 'app/course/shared/sidebar-view.interface';
import { IRIS_SESSION_QUERY_PARAM } from 'app/iris/shared/entities/iris-session.model';

/** Parses a route parameter holding an id, or yields undefined when it is missing or not a number. */
function toId(value: string | undefined): number | undefined {
    if (!value) {
        return undefined;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? undefined : parsed;
}

@Component({
    selector: 'jhi-course-iris',
    templateUrl: './course-iris.component.html',
    styleUrls: ['./course-iris.component.scss'],
    imports: [CourseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseIrisComponent implements SidebarView {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly irisChatService = inject(IrisChatService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly courseChatbot = viewChild('courseChatbot', { read: CourseChatbotComponent });

    private readonly courseIdParam = toSignal((this.route.parent?.paramMap ?? of(convertToParamMap({}))).pipe(map((params) => params.get('courseId') ?? undefined)), {
        initialValue: undefined,
    });

    private readonly sessionIdParam = toSignal(this.route.queryParamMap.pipe(map((params) => params.get(IRIS_SESSION_QUERY_PARAM) ?? undefined)), {
        initialValue: undefined,
    });

    readonly courseId = computed(() => toId(this.courseIdParam()));

    /** The session a link asks to open, e.g. the chat a global search answer was continued in. */
    readonly sessionId = computed(() => toId(this.sessionIdParam()));

    readonly isCollapsed = computed<boolean>(() => !(this.courseChatbot()?.isChatHistoryOpen() ?? true));

    constructor() {
        // When the user opts out of AI from the chat's LLM selection modal while on this page,
        // the Iris course page is no longer useful — send them to the exercises page.
        this.irisChatService.llmOptedOut$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            const id = this.courseId();
            if (id !== undefined) {
                void this.router.navigate(['/courses', id, CourseOverviewRoutePath.EXERCISES]);
            }
        });
    }

    toggleSidebar(): void {
        this.courseChatbot()?.toggleChatHistory();
    }
}
