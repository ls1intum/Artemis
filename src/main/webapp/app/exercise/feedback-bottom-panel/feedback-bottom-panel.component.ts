import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * The panel at the bottom of an exercise page that holds the general feedback of an assessment, shared by the programming,
 * text and modeling pages so it looks and behaves alike on all of them.
 *
 * The header names the panel and shows a collapse button; a page that needs more than a title puts its own content there
 * through `[panelHeader]` (the programming page shows tabs next to the build output). The content is always rendered and only
 * hidden while the panel is collapsed, so what a page keeps in it (e.g. the build output) stays alive.
 *
 * The page owns the collapsed state: it listens to `(toggled)` and passes `[expanded]` back, which lets the programming page tie
 * it to its resizable grid.
 *
 * Theming: a host maps the panel onto its own palette with `--feedback-panel-header-background`,
 * `--feedback-panel-header-border` and `--feedback-panel-header-padding-block`.
 */
@Component({
    selector: 'jhi-feedback-bottom-panel',
    templateUrl: './feedback-bottom-panel.component.html',
    styleUrls: ['./feedback-bottom-panel.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[class.fill]': 'fill()' },
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class FeedbackBottomPanelComponent {
    /** Whether the content is shown; the header always is. */
    readonly expanded = input(true);
    /** The title shown when the page puts no header of its own into `[panelHeader]`. */
    readonly titleKey = input('artemisApp.assessment.generalFeedback');
    /** The number shown next to the default title; left out when not set. */
    readonly count = input<number>();
    /** The accessible name of the collapse button. */
    readonly toggleLabelKey = input('artemisApp.assessment.toggleGeneralFeedback');
    /** Whether the panel fills the height it is given (the programming grid sizes its bottom area) instead of fitting its content. */
    readonly fill = input(false);
    /** Identifies the panel in tests. */
    readonly testId = input('feedback-bottom-panel');

    /** The user clicked the collapse button. */
    readonly toggled = output<MouseEvent>();

    protected readonly faChevronDown = faChevronDown;
    protected readonly faCommentDots = faCommentDots;
}
