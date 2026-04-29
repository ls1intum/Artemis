import { ChangeDetectionStrategy, Component, computed, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { filter, map, pairwise, startWith } from 'rxjs/operators';
import { of } from 'rxjs';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { CourseOverviewRoutePath } from 'app/core/course/overview/courses.route';

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
    private readonly accountService = inject(AccountService);
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

    constructor() {
        // Watch the user's LLM decision via signal observation. Whenever it transitions INTO
        // NO_AI from any prior non-NO_AI value (including `undefined` for first-time opt-out from
        // the modal, or a hydrated `LOCAL_AI` flipping to NO_AI mid-session, or a stored `NO_AI`
        // arriving via async auth on a page they shouldn't be on), the Iris course page is no
        // longer useful — redirect to /exercises.
        //
        // Pairwise filters out the no-op cases (e.g. `[undefined, undefined]` while auth is
        // pending, or `[LOCAL_AI, LOCAL_AI]` redundant emissions). The redirect-on-page-load
        // case for a user who already had NO_AI persisted is correct: they should not be on
        // the iris route at all.
        const llmDecision$ = toObservable(computed(() => this.accountService.userIdentity()?.selectedLLMUsage));
        llmDecision$
            .pipe(
                // `startWith(undefined)` ensures pairwise emits even if the signal is ALREADY at
                // NO_AI when the component subscribes (e.g., in-app navigation with cached auth).
                // Without it, pairwise needs two real emissions, so a user with persisted NO_AI
                // who lands on /iris via URL would never get redirected.
                startWith<LLMSelectionDecision | undefined>(undefined),
                pairwise(),
                filter(([prev, curr]) => prev !== LLMSelectionDecision.NO_AI && curr === LLMSelectionDecision.NO_AI),
                takeUntilDestroyed(),
            )
            .subscribe(() => {
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
