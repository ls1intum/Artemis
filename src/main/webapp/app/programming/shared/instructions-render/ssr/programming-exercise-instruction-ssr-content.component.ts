import { Component, ElementRef, ViewEncapsulation, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import katex from 'katex';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

/**
 * Holds the server-rendered problem statement inside a shadow root, and owns everything that has to touch that DOM.
 *
 * Shadow DOM is used because the render endpoint returns a self-contained stylesheet: the encapsulation scopes it to
 * this component and shields the statement from Artemis' global styles in both directions.
 *
 * Nothing but the server's own markup may live in here. Angular component styles, Tailwind utilities and the UI kit's
 * CSS are all injected into `document.head` and none of that crosses a shadow boundary, so any Angular UI component
 * placed inside this shadow root would render completely unstyled. All chrome (spinner, banners) therefore stays in
 * the parent component, which uses default encapsulation.
 */
@Component({
    selector: 'jhi-programming-exercise-instruction-ssr-content',
    template: '<div #renderTarget class="artemis-problem-statement-host"></div>',
    styles: ':host { display: block; }',
    encapsulation: ViewEncapsulation.ShadowDom,
    host: {
        '(click)': 'onActivate($event)',
        '(keydown.enter)': 'onActivate($event)',
        '(keydown.space)': 'onActivate($event)',
    },
})
export class ProgrammingExerciseInstructionSsrContentComponent {
    private translateService = inject(TranslateService);

    /** The extracted, script-free problem-statement fragment plus its stylesheets. */
    readonly html = input<string>();
    /** The tasks of the current html, in document order; index i belongs to the i-th `.artemis-task` element. */
    readonly tasks = input<SsrTask[]>([]);
    /** Whether a task can currently open a feedback dialog. Drives `role` / `tabindex` / `aria-label`. */
    readonly interactive = input(false);

    /** Emits the document position of an activated task. */
    readonly taskActivated = output<number>();

    private readonly renderTarget = viewChild.required<ElementRef<HTMLElement>>('renderTarget');

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
            // Only the interactivity gating changed, for example because a participation arrived while the server output
            // stayed byte-identical (possibly served from the render cache). Refreshing just the task attributes keeps
            // scroll position, focus and the already rendered formulas untouched.
            this.applyTaskAccessibility(host, tasks, interactive);
            return;
        }
        // Captured before the swap: replacing the markup detaches the focused node and resets the scroll position.
        const focusedTaskIndex = this.focusedTaskIndex(host);
        const scrollTop = this.scrollParent()?.scrollTop;
        this.currentHtml = html;
        // The markup is server-generated, sanitized server-side with a jsoup safelist, and every <script> was removed
        // by the parent before it reached this component. That is the same trust decision a `bypassSecurityTrustHtml` binding
        // would encode. It is written imperatively rather than through `[innerHTML]` because a template binding is
        // applied during this component's view refresh, which Angular runs *before* the view's effects; the focus and
        // scroll capture above would then already be looking at the replaced DOM.
        host.innerHTML = html ?? '';
        this.renderFormulas(host);
        this.applyTaskAccessibility(host, tasks, interactive);
        const scrollParent = this.scrollParent();
        if (scrollTop !== undefined && scrollParent) {
            scrollParent.scrollTop = scrollTop;
        }
        if (focusedTaskIndex !== undefined) {
            this.taskElements(host)[focusedTaskIndex]?.focus();
        }
    }

    /**
     * Renders the inert `<span class="katex-formula" data-formula data-display-mode>` placeholders the server emits.
     * The server's own KaTeX script is stripped, so the math has to be produced here.
     */
    private renderFormulas(host: HTMLElement): void {
        host.querySelectorAll<HTMLElement>('.katex-formula').forEach((element) => {
            const formula = element.getAttribute('data-formula') ?? '';
            try {
                katex.render(formula, element, { displayMode: element.getAttribute('data-display-mode') === 'true', throwOnError: false, output: 'html' });
            } catch {
                element.textContent = formula;
            }
        });
    }

    /**
     * Marks tasks as buttons only while a feedback dialog can actually be opened. `aria-label` is set exclusively on
     * the interactive branch: ARIA prohibits it on `role=generic`, and most screen readers ignore it there anyway.
     */
    private applyTaskAccessibility(host: HTMLElement, tasks: SsrTask[], interactive: boolean): void {
        this.taskElements(host).forEach((element, index) => {
            const task = tasks[index];
            if (interactive && task?.testIds.length) {
                element.setAttribute('role', 'button');
                element.setAttribute('tabindex', '0');
                element.setAttribute('aria-label', this.taskAriaLabel(task));
            } else {
                element.removeAttribute('role');
                element.removeAttribute('tabindex');
                element.removeAttribute('aria-label');
            }
        });
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
        if (!active) {
            return undefined;
        }
        const index = this.taskElements(host).indexOf(active as HTMLElement);
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

    /**
     * Resolves a click or keyboard activation inside the shadow root to the task it happened on.
     *
     * Events are retargeted at the shadow boundary, so `event.target` is this host element seen from the outside;
     * `composedPath()` still contains the real node inside the shadow tree.
     */
    onActivate(event: Event): void {
        const taskElement = event.composedPath().find((target): target is HTMLElement => target instanceof HTMLElement && target.classList.contains('artemis-task'));
        if (!taskElement) {
            return;
        }
        // Resolve by document position, not by name: task names are not guaranteed to be unique.
        const index = this.taskElements(this.renderTarget().nativeElement).indexOf(taskElement);
        if (index !== -1 && this.tasks()[index]) {
            event.preventDefault();
            this.taskActivated.emit(index);
        }
    }
}
