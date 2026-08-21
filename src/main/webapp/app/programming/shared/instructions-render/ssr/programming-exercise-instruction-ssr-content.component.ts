import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { FRAME_PROTOCOL_VERSION } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-script';
import { resolveFrameLink } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame.util';
import { FRAME_SANDBOX } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-policy';

/**
 * Above this the reported height is treated as hostile rather than long. The tallest statement in the corpus is
 * two orders of magnitude below it, so nothing legitimate reaches it. A document that did would keep this box
 * and scroll inside the frame, which is a worse reading experience but never a clipped statement.
 */
const MAX_FRAME_HEIGHT_PX = 50_000;

/**
 * Holds the server-rendered problem statement inside a sandboxed iframe, and owns the conversation with it.
 *
 * The frame is the point of the design. Its `sandbox` carries `allow-scripts` and nothing else: no
 * `allow-same-origin`, so the document inside has an opaque origin and can reach no cookie, no storage, no
 * parent DOM and no authenticated API response; no `allow-popups`, `allow-forms`, `allow-modals`,
 * `allow-downloads` or `allow-top-navigation`. Together with the per-frame CSP that
 * `problem-statement-frame.util.ts` writes into the document, a bypass of the server safelist and of DOMPurify
 * stops being a session compromise and becomes a defaced pane.
 *
 * `allow-scripts` is present because one script has to run in there: the frame cannot report its own height,
 * resolve a click to a task, or hand a link to the parent without one. That script is the only element in the
 * document carrying the CSP nonce, so nothing an attacker smuggles in can execute alongside it.
 *
 * All chrome (spinner, banners, step wizard) stays in the parent component. The frame is a separate document
 * and none of the application's styles reach into it, which is exactly why the server ships the statement's
 * stylesheet with it.
 */
@Component({
    selector: 'jhi-programming-exercise-instruction-ssr-content',
    template: `<iframe
        #frame
        class="artemis-statement-frame"
        sandbox="allow-scripts"
        allow=""
        referrerpolicy="no-referrer"
        [attr.title]="'artemisApp.programmingExercise.problemStatement.frameTitle' | artemisTranslate"
    ></iframe>`,
    styleUrls: ['./programming-exercise-instruction-ssr-content.component.scss'],
    imports: [ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseInstructionSsrContentComponent implements OnDestroy {
    private translateService = inject(TranslateService);

    /**
     * The value the template's static `sandbox` attribute must carry.
     *
     * Angular refuses to let `sandbox` be a binding at all (NG0910), which is the right call: it means the frame's
     * privileges cannot be changed at runtime. The constant is kept beside it so the browser tests assert the
     * shipped value rather than a copy, and a unit test pins the two together.
     */
    readonly expectedSandbox = FRAME_SANDBOX;

    /** The complete sandboxed document, as assembled by `assembleFrameDocument`. */
    readonly srcdoc = input<string>();
    /** Token of the current document; a message not carrying it belongs to a frame that has been replaced. */
    readonly generation = input<string>('');
    /** The tasks of the current document, in document order. */
    readonly tasks = input<SsrTask[]>([]);
    /** Whether a task can currently open a feedback dialog. Drives `role` / `tabindex` / `aria-label`. */
    readonly interactive = input(false);
    /** The hrefs present in the current document; the frame may only ask the parent to open one of these. */
    readonly linkTargets = input<readonly string[]>([]);

    /** Emits the document position of an activated task. */
    readonly taskActivated = output<number>();

    private readonly frame = viewChild.required<ElementRef<HTMLIFrameElement>>('frame');

    /** Set once the current frame's script has announced itself; reset whenever the document is replaced. */
    private readonly frameReady = signal(false);

    private lastAppliedHeight = 0;

    /**
     * The task the reader last focused inside the frame, carried across a reload.
     *
     * Replacing `srcdoc` loads a new document, so focus is lost exactly when the statement is re-rendered, which
     * for a keyboard reader is every time a result arrives. Deliberately not reset when the document changes:
     * carrying it over is the whole point.
     */
    private lastFocusedTaskIndex?: number;

    constructor() {
        window.addEventListener('message', this.onFrameMessage);

        effect(() => {
            const srcdoc = this.srcdoc() ?? '';
            untracked(() => {
                // Assigned imperatively, never as a template binding. Angular treats `iframe.srcdoc` as an HTML
                // security context and runs its sanitizer over a bound value, which strips the `<meta>` policy and
                // the nonced script and leaves a document of a few characters. That silently disables the entire
                // isolation design while every string-level assertion still passes, so the value has to reach the
                // element untouched. It is safe by construction: this component assembled it, and it is destined
                // for a frame that may do nothing with it.
                this.frame().nativeElement.srcdoc = srcdoc;
                // Loading a new document takes the old one's script with it, and everything it had been told.
                this.frameReady.set(false);
                this.lastAppliedHeight = 0;
            });
        });

        effect(() => {
            const tasks = this.tasks();
            const interactive = this.interactive();
            const ready = this.frameReady();
            if (ready) {
                untracked(() => this.sendInteractiveState(tasks, interactive));
            }
        });
    }

    ngOnDestroy(): void {
        window.removeEventListener('message', this.onFrameMessage);
    }

    /**
     * Bound rather than a method so it can be removed again, and so `this` is the component when the browser
     * calls it.
     *
     * The frame's origin is `"null"`, which authenticates nothing, so the sender is identified by comparing
     * against this component's own frame instead, and the payload is checked field by field. A message that
     * passes all of it can still only do what a reader could: open a feedback dialog for a task that exists,
     * resize the box, or follow a link that is in the document.
     */
    private readonly onFrameMessage = (event: MessageEvent): void => {
        const contentWindow = this.frame().nativeElement.contentWindow;
        if (!contentWindow || event.source !== contentWindow) {
            return;
        }
        const data = event.data;
        if (!data || typeof data !== 'object' || data.v !== FRAME_PROTOCOL_VERSION || data.gen !== this.generation()) {
            return;
        }
        switch (data.type) {
            case 'ready':
                this.frameReady.set(true);
                this.sendInteractiveState(this.tasks(), this.interactive());
                break;
            case 'height':
                this.applyHeight(data.px);
                break;
            case 'task':
                this.activateTask(data.index);
                break;
            case 'link':
                this.openLink(data.href);
                break;
            case 'focus':
                if (Number.isInteger(data.index) && data.index >= 0) {
                    this.lastFocusedTaskIndex = data.index;
                }
                break;
        }
    };

    /**
     * Emits an activation only for a task this component currently declares interactive.
     *
     * A bounds check alone would let a hostile frame ask for a task the reader is not being offered. The parent
     * refuses such an activation as well, but the guarantee belongs here, where the message arrives, rather than
     * resting on what a caller happens to check further down.
     */
    private activateTask(index: unknown): void {
        if (!Number.isInteger(index)) {
            return;
        }
        const task = this.tasks()[index as number];
        if (this.interactive() && task?.testIds.length) {
            this.taskActivated.emit(index as number);
        }
    }

    private applyHeight(px: unknown): void {
        if (typeof px !== 'number' || !Number.isFinite(px) || px <= 0) {
            return;
        }
        const height = Math.min(Math.ceil(px), MAX_FRAME_HEIGHT_PX);
        // A ResizeObserver fires for every layout pass; re-assigning the same height would only invalidate
        // layout again and, on a bounded host, fight the parent's scroll position for no gain.
        if (height === this.lastAppliedHeight) {
            return;
        }
        this.lastAppliedHeight = height;
        this.frame().nativeElement.style.height = `${height}px`;
    }

    private openLink(href: unknown): void {
        if (typeof href !== 'string') {
            return;
        }
        const target = resolveFrameLink(href, this.linkTargets(), window.location.origin);
        if (target) {
            window.open(target, '_blank', 'noopener,noreferrer');
        }
    }

    /**
     * Tells the frame which tasks are interactive, with their labels already translated: the frame has no
     * translation service and must not have one.
     */
    private sendInteractiveState(tasks: SsrTask[], interactive: boolean): void {
        const contentWindow = this.frame().nativeElement.contentWindow;
        if (!contentWindow) {
            return;
        }
        const payload = interactive ? tasks.filter((task) => task.testIds.length).map((task) => ({ index: task.index, label: this.taskAriaLabel(task) })) : [];
        // Only offered while the index still exists: a shorter statement would otherwise focus nothing and, worse,
        // silently move the reader somewhere they never were.
        const focusIndex = this.lastFocusedTaskIndex !== undefined && this.lastFocusedTaskIndex < tasks.length ? this.lastFocusedTaskIndex : undefined;
        // The frame has an opaque origin, so there is no origin to address it by: a targetOrigin other than `*`
        // would never match and the message would simply be dropped. The frame validates that the message came
        // from its parent instead. Nothing sensitive travels this way either, only task indices and labels that
        // are already rendered in the statement.
        // nosemgrep -- wildcard targetOrigin is forced by the sandboxed frame's opaque origin; see above
        contentWindow.postMessage({ type: 'interactive', tasks: payload, focusIndex }, '*');
    }

    private taskAriaLabel(task: SsrTask): string {
        // Own key set: artemisApp.editor.testStatusLabels only defines noResult, noTests, testPassing and
        // totalTestsPassing, so there is no existing key for "failed" or "not executed".
        return `${task.taskName}: ${this.translateService.instant('artemisApp.programmingExercise.problemStatement.taskStatus.' + task.status)}`;
    }
}
