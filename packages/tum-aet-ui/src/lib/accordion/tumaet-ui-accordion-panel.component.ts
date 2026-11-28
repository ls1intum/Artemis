import { ChangeDetectionStrategy, Component, DestroyRef, booleanAttribute, effect, inject, input, model, numberAttribute, untracked } from '@angular/core';
import { AccordionPanel, AccordionTrigger } from '@angular/aria/accordion';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiAccordionComponent } from './tumaet-ui-accordion.component';

/**
 * One section of a {@link TumAetUiAccordionComponent}: a header button and the content it shows or hides.
 *
 * The Angular Aria accordion trigger and panel own the button semantics, `aria-expanded`, `aria-controls`, the region
 * role and its label, and they make collapsed content inert. Collapsed content stays in the document, so it keeps its
 * state and the height animates in both directions; the animation is skipped for users who prefer reduced motion.
 *
 * Give the header as text, or project markup with `tumAetUiAccordionHeader`:
 *
 * ```html
 * <tumaet-ui-accordion-panel [(expanded)]="open">
 *     <span tumAetUiAccordionHeader>Grading <tumaet-ui-tag value="New" /></span>
 *     Submissions are graded automatically.
 * </tumaet-ui-accordion-panel>
 * ```
 *
 * The header inherits font and text colour from the host, and the host carries the border, radius and background, so
 * an application styles those with classes on the host; `data-expanded` reflects the state. The inner spacing and
 * the chevron follow four custom properties, each with a default: `--tumaet-ui-accordion-header-padding`,
 * `--tumaet-ui-accordion-content-padding`, `--tumaet-ui-accordion-icon-color` and `--tumaet-ui-accordion-icon-size`.
 */
@Component({
    selector: 'tumaet-ui-accordion-panel',
    templateUrl: './tumaet-ui-accordion-panel.component.html',
    styleUrl: './tumaet-ui-accordion-panel.component.css',
    imports: [AccordionTrigger, AccordionPanel, FaIconComponent],
    host: {
        class: 'tumaet-ui-accordion-panel tumaet:block tumaet:rounded-md tumaet:border tumaet:border-border tumaet:bg-content-background tumaet:text-text',
        '[attr.data-expanded]': 'expanded()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiAccordionPanelComponent {
    private readonly accordion = inject(TumAetUiAccordionComponent, { optional: true });

    /** Header text. Omit it when projecting a `[tumAetUiAccordionHeader]` element instead. */
    readonly header = input<string>('');

    /** Whether the content is shown. In an accordion that is not `multiExpandable`, opening a panel closes the others. */
    readonly expanded = model(false);

    /** Keeps the panel from being toggled. The header stays focusable and is announced as unavailable. */
    readonly disabled = input(false, { transform: booleanAttribute });

    /** Level of the heading that wraps the header button, between 1 and 6, matching the surrounding document outline. */
    readonly headingLevel = input(3, { transform: numberAttribute });

    protected readonly faChevronDown = faChevronDown;

    constructor() {
        const accordion = this.accordion;
        if (accordion) {
            inject(DestroyRef).onDestroy(accordion.register(this));
            effect(() => {
                if (this.expanded()) {
                    untracked(() => accordion.panelOpened(this));
                }
            });
        }
    }
}
