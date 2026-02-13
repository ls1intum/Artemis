import { ChangeDetectionStrategy, Component, computed, inject, viewChild } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { of } from 'rxjs';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';

@Component({
    selector: 'jhi-course-iris',
    templateUrl: './course-iris.component.html',
    styleUrls: ['./course-iris.component.scss'],
    imports: [CourseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseIrisComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly courseChatbot = viewChild('courseChatbot', { read: CourseChatbotComponent });

    private readonly courseIdParam = toSignal((this.route.parent?.paramMap ?? of(convertToParamMap({}))).pipe(map((params) => params.get('courseId') ?? undefined)), {
        initialValue: undefined,
    });

    readonly courseId = computed(() => {
        const value = this.courseIdParam();
        if (!value) {
            return undefined;
        }
        const parsed = Number(value);
        return Number.isNaN(parsed) ? undefined : parsed;
    });

    isCollapsed = false;

    toggleSidebar(): void {
        this.courseChatbot()?.toggleChatHistory();
        this.isCollapsed = !this.isCollapsed;
    }
}
