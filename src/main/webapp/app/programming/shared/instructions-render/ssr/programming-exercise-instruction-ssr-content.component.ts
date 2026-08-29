import { ChangeDetectionStrategy, Component, ElementRef, ViewEncapsulation, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { resolveFrameLink } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame.util';

/**
 * The class the client sets on a task it has declared interactive.
 *
 * `stripSensitiveAttributes` removes `data-feedback` (it carries the student's own feedback), and the server
 * stylesheet keys the default cursor off `.artemis-task:not([data-feedback])`, so without this class every task
 * would show the default cursor. `embedded.css` gives `.artemis-task.artemis-task--interactive` the pointer back.
 */
const INTERACTIVE_TASK_CLASS = 'artemis-task--interactive';

/**
 * Holds the server-rendered problem statement inside a shadow root, and owns everything that touches that DOM.
 *
 * Shadow DOM is used because the render endpoint returns a self-contained stylesheet: the encapsulation scopes it to
 * this component and shields the statement from Artemis' global styles in both directions. It is CSS isolation, not a
 * security boundary: the shadow root is same-origin, so the markup here shares the page's credentials. What keeps a
 * sanitizer bypass from being a session compromise is that `assembleShadowContent` never lets a script or an
 * event handler survive into this DOM, not any origin boundary. That is the trade the project accepted when it
 * dropped the sandboxed frame in favour of the double jsoup + DOMPurify sanitization.
 *
 * Nothing but the server's own markup and its own stylesheets may live in here. Other components' styles, Tailwind
 * utilities and the UI kit's CSS are injected into `document.head` and none of that crosses a shadow boundary, so an
 * Angular UI component placed inside this shadow root would render completely unstyled. All chrome (spinner, banners,
 * step wizard) therefore stays in the parent component, which uses default encapsulation.
 */
@Component({
    selector: 'jhi-programming-exercise-instruction-ssr-content',
    template: '<div #renderTarget class="artemis-problem-statement-host"></div>',
    styleUrls: ['./programming-exercise-instruction-ssr-content.component.scss'],
    encapsulation: ViewEncapsulation.ShadowDom,
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        '(click)': 'onActivate($event)',
        '(keydown.enter)': 'onActivate($event)',
        '(keydown.space)': 'onActivate($event)',
    },
})
export class ProgrammingExerciseInstructionSsrContentComponent {
    private translateService = inject(TranslateService);

    /** The renderable statement (server stylesheets + wrapper + sanitized fragment) from `assembleShadowContent`. */
    readonly html = input<string>();
    /** The tasks of the current html, in document order; index i belongs to the i-th `.artemis-task` element. */
    readonly tasks = input<SsrTask[]>([]);
    /** Whether a task can currently open a feedback dialog. Drives `role` / `tabindex` / `aria-label` / cursor. */
    readonly interactive = input(false);
    /** The hrefs present in the current statement; a click may only open one of these. */
    readonly linkTargets = input<readonly string[]>([]);

    /** Emits the document position of an activated task. */
    readonly taskActivated = output<number>();

    private readonly renderTarget = viewChild.required<ElementRef<HTMLElement>>('renderTarget');

    /** The html currently in the shadow root, so an unchanged render only refreshes the task attributes. */
    private currentHtml?: string;

    constructor() {
        effect(() => {
            const html = this.html();
            const tasks = this.tasks();
            const interactive = this.interactive();
            untracked(() => this.applyToDom(html, tasks, interactive));
        });
    }

    private applyToDom(html: string | undefined, tasks: SsrTask[], interactive: boolean): void {
        const host = this.renderTarget().nativeElement;
        if (html === this.currentHtml) {
            // Only the interactivity gating changed, for example because a participation arrived while the server
            // output stayed byte-identical. Refreshing just the task attributes keeps scroll position, focus and the
            // already rendered formulas untouched.
            this.applyTaskAccessibility(host, tasks, interactive);
            return;
        }
        // Captured before the swap: replacing the markup detaches the focused node and resets the scroll position.
        const focusedTaskIndex = this.focusedTaskIndex(host);
        const scrollParent = this.scrollParent();
        const scrollTop = scrollParent?.scrollTop;
        this.currentHtml = html;
        // The value is `assembleShadowContent` output: the server's own document-level stylesheets plus a DOMPurify
        // fragment run through the safe producers in problem-statement-frame.util.ts (strip, MathML harden, image
        // rewrite, highlight), each of which emits escaped markup. That is the same trust decision a `bypassSecurityTrustHtml`
        // binding would encode, written imperatively rather than through `[innerHTML]` because a template binding is
        // applied during this component's view refresh, which Angular runs before the view's effects; the focus and
        // scroll capture above would then already be looking at the replaced DOM.
        // nosemgrep -- the value is assembleShadowContent output; see problem-statement-frame.util.ts
        host.innerHTML = html ?? '';
        this.applyTaskAccessibility(host, tasks, interactive);
        if (scrollTop !== undefined && scrollParent) {
            scrollParent.scrollTop = scrollTop;
        }
        if (focusedTaskIndex !== undefined) {
            this.taskElements(host)[focusedTaskIndex]?.focus();
        }
    }

    /**
     * Marks tasks as buttons only while a feedback dialog can actually be opened. `aria-label` and the interactive
     * class are set exclusively on that branch: ARIA prohibits `aria-label` on `role=generic`, and a non-interactive
     * task must keep the default cursor.
     */
    private applyTaskAccessibility(host: HTMLElement, tasks: SsrTask[], interactive: boolean): void {
        this.taskElements(host).forEach((element, index) => {
            const task = tasks[index];
            if (interactive && task?.testIds.length) {
                element.setAttribute('role', 'button');
                element.setAttribute('tabindex', '0');
                element.setAttribute('aria-label', this.taskAriaLabel(task));
                element.classList.add(INTERACTIVE_TASK_CLASS);
            } else {
                element.removeAttribute('role');
                element.removeAttribute('tabindex');
                element.removeAttribute('aria-label');
                element.classList.remove(INTERACTIVE_TASK_CLASS);
            }
        });
    }

    /**
     * Resolves a click or keyboard activation inside the shadow root to a task or a link.
     *
     * Events are retargeted at the shadow boundary, so `event.target` is this host element seen from the outside;
     * `composedPath()` still contains the real node inside the shadow tree. A task takes precedence over a link
     * nested inside it. Links work regardless of the interactivity gating, and every statement anchor has its
     * default navigation cancelled before validation, so an anchor that is not a known link target simply does
     * nothing rather than navigating the whole application away.
     */
    onActivate(event: Event): void {
        const path = event.composedPath();

        const taskElement = path.find((target): target is HTMLElement => target instanceof HTMLElement && target.classList.contains('artemis-task'));
        if (taskElement && this.interactive()) {
            // Resolve by document position, not by name: task names are not guaranteed to be unique.
            const index = this.taskElements(this.renderTarget().nativeElement).indexOf(taskElement);
            if (index !== -1 && this.tasks()[index]?.testIds.length) {
                event.preventDefault();
                this.taskActivated.emit(index);
                return;
            }
        }

        // Keyboard activation of an anchor already arrives as a synthesized click, so links are handled on click only.
        if (event.type !== 'click') {
            return;
        }
        // Matched by `localName`, not `tagName === 'A'` on an `HTMLElement`: an SVG `<a>` (PlantUML emits inline SVG)
        // is an `SVGElement` and would otherwise slip through, keep its default, and navigate the whole application
        // away. Its href may live in `xlink:href` rather than `href`.
        const anchor = path.find(
            (target): target is Element => target instanceof Element && target.localName === 'a' && (target.hasAttribute('href') || target.hasAttribute('xlink:href')),
        );
        if (!anchor) {
            return;
        }
        // Cancel native navigation for every statement anchor before validating it: an unknown or unexpected href
        // must not fall through to navigating the application.
        event.preventDefault();
        const href = anchor.getAttribute('href') ?? anchor.getAttribute('xlink:href') ?? '';
        const target = resolveFrameLink(href, this.linkTargets(), window.location.origin);
        if (target) {
            window.open(target, '_blank', 'noopener,noreferrer');
        }
    }

    private taskAriaLabel(task: SsrTask): string {
        // Own key set: artemisApp.editor.testStatusLabels only defines noResult, noTests, testPassing and
        // totalTestsPassing, so there is no existing key for "failed" or "not executed".
        return `${task.taskName}: ${this.translateService.instant('artemisApp.programmingExercise.problemStatement.taskStatus.' + task.status)}`;
    }

    private taskElements(host: HTMLElement): HTMLElement[] {
        return [...host.querySelectorAll<HTMLElement>('.artemis-task')];
    }

    /** Index of the currently focused task inside the shadow root, so focus can be restored after a re-render. */
    private focusedTaskIndex(host: HTMLElement): number | undefined {
        const active = (host.getRootNode() as ShadowRoot | undefined)?.activeElement;
        // The nearest containing task, not the exact element: focus may sit on a link or another focusable node
        // inside a task, and that reader is still "on" the task and must land back on it after the re-render.
        const task = active?.closest('.artemis-task') as HTMLElement | null;
        if (!task) {
            return undefined;
        }
        const index = this.taskElements(host).indexOf(task);
        return index === -1 ? undefined : index;
    }

    /** The nearest scrollable ancestor outside the shadow root, whose position must survive a full re-render. */
    private scrollParent(): HTMLElement | undefined {
        let node = (this.renderTarget().nativeElement.getRootNode() as ShadowRoot | undefined)?.host?.parentElement ?? undefined;
        while (node) {
            if (node.scrollHeight > node.clientHeight && ['auto', 'scroll'].includes(getComputedStyle(node).overflowY)) {
                return node;
            }
            node = node.parentElement ?? undefined;
        }
        return undefined;
    }
}
