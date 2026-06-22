import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { map } from 'rxjs/operators';
import { of } from 'rxjs';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { CourseOverviewRoutePath } from 'app/course/overview/courses.route';

@Component({
    selector: 'jhi-course-iris',
    templateUrl: './course-iris.component.html',
    styleUrls: ['./course-iris.component.scss'],
    imports: [CourseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseIrisComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly irisChatService = inject(IrisChatService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly courseChatbot = viewChild('courseChatbot', { read: CourseChatbotComponent });

    private readonly courseIdParam = toSignal((this.route.parent?.paramMap ?? of(convertToParamMap({}))).pipe(map((params) => params.get('courseId') ?? undefined)), {
        initialValue: undefined,
    });

    readonly courseId = computed(() => {
        const value = this.courseIdParam();
        if (!value) return undefined;
        const parsed = Number(value);
        return Number.isNaN(parsed) ? undefined : parsed;
    });

    // irisContext query param format: 'lecture:{id}' or 'exercise:{id}'
    private readonly irisContextParam = toSignal(this.route.queryParamMap.pipe(map((params) => params.get('irisContext') ?? undefined)), { initialValue: undefined });

    readonly initialContextType = computed<string | undefined>(() => this.irisContextParam()?.split(':')[0]);

    readonly initialContextEntityId = computed<number | undefined>(() => {
        const raw = this.irisContextParam()?.split(':')[1];
        if (!raw) return undefined;
        const parsed = Number(raw);
        return Number.isNaN(parsed) ? undefined : parsed;
    });

    isCollapsed = false;

    constructor() {
        // When the user opts out of AI from the chat's LLM selection modal while on this page,
        // the Iris course page is no longer useful — send them to the exercises page.
        this.irisChatService.llmOptedOut$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            const id = this.courseId();
            if (id !== undefined) {
                this.router.navigate(['/courses', id, CourseOverviewRoutePath.EXERCISES]);
            }
        });
    }

    toggleSidebar(): void {
        this.courseChatbot()?.toggleChatHistory();
        this.isCollapsed = !this.isCollapsed;
    }
}
