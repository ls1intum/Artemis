import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { IrisHandoffTarget, chatAnswer, handoffTarget } from 'app/core/navbar/global-search/util/iris-chat-handoff.util';
import { CitedSources } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IRIS_SESSION_QUERY_PARAM } from 'app/iris/shared/entities/iris-session.model';

/**
 * Continues a finished global search answer in the Iris chat of the course it came from, on the lecture or exercise the answer
 * cites most, by button or by Cmd/Ctrl+Enter. The chat is created with the question and the answer already in it before
 * navigating, so it opens complete.
 */
@Component({
    selector: 'jhi-global-search-iris-handoff',
    templateUrl: './global-search-iris-handoff.component.html',
    styleUrl: './global-search-iris-handoff.component.scss',
    imports: [TumAetUiTooltipDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '(window:keydown)': 'onWindowKeydown($event)' },
})
export class GlobalSearchIrisHandoffComponent {
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly router = inject(Router);
    private readonly osDetector = inject(OsDetectorService);

    /** The question the student asked. */
    readonly question = input.required<string>();
    /** The finished answer, with its `[n]` citation markers. */
    readonly answer = input.required<string>();
    /** The sources the answer cites, numbered the way its markers are. */
    readonly citedSources = input.required<CitedSources>();

    protected readonly target = computed(() => handoffTarget(this.citedSources()));
    /** Set while the chat is being created, so a double click creates one chat rather than two. */
    protected readonly isOpening = signal(false);
    protected readonly shortcutLabel = computed(() => `${this.osDetector.actionKeyLabel()}↵`);

    /** Cmd/Ctrl+Enter, the same as the button; the palette's own Enter handlers leave the modified key alone. */
    protected onWindowKeydown(event: KeyboardEvent): void {
        const target = this.target();
        if (event.key !== 'Enter' || !this.osDetector.isActionKey(event) || event.repeat || !target) {
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
            })
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
