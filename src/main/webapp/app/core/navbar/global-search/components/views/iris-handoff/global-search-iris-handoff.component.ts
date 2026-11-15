import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faComment } from '@fortawesome/free-regular-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { IrisHandoffTarget, chatAnswer, handoffTarget } from 'app/core/navbar/global-search/util/iris-chat-handoff.util';
import { CitedSources } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IRIS_SESSION_QUERY_PARAM } from 'app/iris/shared/entities/iris-session.model';

let nextHandoffId = 0;

/**
 * Continues a finished global search answer in the Iris chat of the course it came from, on the lecture or exercise the answer
 * cites most, by button or by Cmd/Ctrl+Enter. The chat is created with the question and the answer already in it before
 * navigating, so it opens complete.
 */
@Component({
    selector: 'jhi-global-search-iris-handoff',
    templateUrl: './global-search-iris-handoff.component.html',
    styleUrl: './global-search-iris-handoff.component.scss',
    imports: [FaIconComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '(window:keydown)': 'onWindowKeydown($event)' },
})
export class GlobalSearchIrisHandoffComponent {
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly router = inject(Router);
    private readonly osDetector = inject(OsDetectorService);
    private readonly destroyRef = inject(DestroyRef);

    /** The question the student asked. */
    readonly question = input.required<string>();
    /** The finished answer, with its `[n]` citation markers. */
    readonly answer = input.required<string>();
    /** The sources the answer cites, numbered the way its markers are. */
    readonly citedSources = input.required<CitedSources>();
    /** The global search run that produced the answer, so the chat's citations are pinned to the material the answer was written from. */
    readonly runId = input<string>();

    protected readonly faComment = faComment;
    protected readonly target = computed(() => handoffTarget(this.citedSources()));
    /** Set while the chat is being created, so a double click creates one chat rather than two. */
    protected readonly isOpening = signal(false);
    protected readonly shortcutLabel = computed(() => `${this.osDetector.actionKeyLabel()}↵`);
    /** The shortcut as assistive technology names it; the keycap itself is decorative. */
    protected readonly keyShortcuts = computed(() => (this.osDetector.isMac() ? 'Meta+Enter' : 'Control+Enter'));
    /** Links the action to the prompt that describes where it leads. */
    protected readonly promptId = `iris-handoff-prompt-${nextHandoffId++}`;

    /** Cmd/Ctrl+Enter, the same as the button; the palette's own Enter handlers leave the modified key alone. */
    protected onWindowKeydown(event: KeyboardEvent): void {
        const target = this.target();
        if (event.key !== 'Enter' || !this.osDetector.isActionKey(event) || event.repeat || event.defaultPrevented || !target) {
            return;
        }
        event.preventDefault();
        this.continueIn(target);
    }

    protected continueIn(target: IrisHandoffTarget): void {
        if (this.isOpening()) {
            return;
        }
        this.isOpening.set(true);
        this.irisChatHttpService
            .createSessionFromGlobalSearch({
                courseId: target.courseId,
                context: target.context,
                question: this.question().trim(),
                answer: chatAnswer(this.answer(), this.citedSources()),
                runId: this.runId(),
            })
            // A chat created after the palette was closed or the search changed must not pull the student away from what they do now.
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                // Navigating closes the search palette, and this button with it.
                next: (session) =>
                    void this.router
                        .navigate(['/courses', target.courseId, 'iris'], { queryParams: { [IRIS_SESSION_QUERY_PARAM]: session.id } })
                        .finally(() => this.isOpening.set(false)),
                // The failure itself is reported by the global HTTP error alert; the button only becomes usable again.
                error: () => this.isOpening.set(false),
            });
    }
}
