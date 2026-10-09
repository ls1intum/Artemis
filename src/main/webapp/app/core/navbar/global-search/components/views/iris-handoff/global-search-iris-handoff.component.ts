import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faArrowRight, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiButtonGroupComponent, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { iconForEntityType } from 'app/core/navbar/global-search/util/entity-type-icons.util';
import { IrisHandoffTarget, chatAnswer, handoffOptions } from 'app/core/navbar/global-search/util/iris-chat-handoff.util';
import { CitedSources } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IRIS_SESSION_QUERY_PARAM } from 'app/iris/shared/entities/iris-session.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

/**
 * Continues a finished global search answer in the Iris chat of the course it came from. The button goes to the place the answer
 * cites most and names it; when the answer cites several courses, or several lectures and exercises, a menu next to it offers
 * the others. The chat is created with the question and the answer already in it before navigating, so it opens complete.
 */
@Component({
    selector: 'jhi-global-search-iris-handoff',
    templateUrl: './global-search-iris-handoff.component.html',
    imports: [
        TumAetUiButtonDirective,
        TumAetUiButtonGroupComponent,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GlobalSearchIrisHandoffComponent {
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly router = inject(Router);

    /** The question the student asked. */
    readonly question = input.required<string>();
    /** The finished answer, with its `[n]` citation markers. */
    readonly answer = input.required<string>();
    /** The sources the answer cites, numbered the way its markers are. */
    readonly citedSources = input.required<CitedSources>();

    protected readonly options = computed(() => handoffOptions(this.citedSources()));
    /** Set while the chat is being created, so a double click creates one chat rather than two. */
    protected readonly isOpening = signal(false);

    protected readonly faArrowRight = faArrowRight;
    protected readonly faChevronDown = faChevronDown;

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

    /** The palette's icon for the kind of place, so a lecture or exercise looks the same here as in the results. */
    protected iconFor(target: IrisHandoffTarget): IconDefinition {
        switch (target.context?.mode) {
            case ChatServiceMode.LECTURE:
                return iconForEntityType('lecture');
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return iconForEntityType('exercise', 'programming');
            case ChatServiceMode.TEXT_EXERCISE:
                return iconForEntityType('exercise', 'text');
            default:
                return iconForEntityType('course');
        }
    }
}
