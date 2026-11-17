import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { AccordionGroup } from '@angular/aria/accordion';

/**
 * A vertical stack of {@link TumAetUiAccordionPanelComponent}s, each with a header that shows or hides its content.
 *
 * The arrow-key, Home and End navigation between the headers and the expansion mode come from the Angular Aria
 * accordion group; this component owns the layout. One panel is open at a time unless `multiExpandable` is set:
 *
 * ```html
 * <tumaet-ui-accordion>
 *     <tumaet-ui-accordion-panel header="Which languages are supported?">Java, Python, C, and more.</tumaet-ui-accordion-panel>
 *     <tumaet-ui-accordion-panel header="Is it free to use?" [(expanded)]="pricingOpen">Yes.</tumaet-ui-accordion-panel>
 * </tumaet-ui-accordion>
 * ```
 *
 * `disabled` disables every panel, and `wrap` lets the arrow keys continue from the last header to the first. Both
 * take a boolean binding, as does `multiExpandable`. Use `tumaet-ui-panel` instead for a single collapsible section.
 */
@Component({
    selector: 'tumaet-ui-accordion',
    template: '<ng-content />',
    hostDirectives: [{ directive: AccordionGroup, inputs: ['disabled', 'wrap'] }],
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/accordion/testing`) find the group.
        ngAccordionGroup: '',
        class: 'tumaet-ui-accordion tumaet:flex tumaet:flex-col tumaet:gap-2',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiAccordionComponent {
    /**
     * Whether several panels may be open at once. By default opening a panel closes the others.
     *
     * The aria group allows several open panels and reads that setting once while it is constructed, so it cannot be
     * given another default. The accordion therefore leaves aria in its permissive mode and closes the other panels
     * itself.
     */
    readonly multiExpandable = input(false, { transform: booleanAttribute });

    private readonly panels = new Set<TumAetUiAccordionPanel>();

    /** @internal Adds a panel of this accordion and returns the function that removes it again. */
    register(panel: TumAetUiAccordionPanel): () => void {
        this.panels.add(panel);
        return () => this.panels.delete(panel);
    }

    /** @internal Closes every other panel once `opened` was expanded, unless several may be open. */
    panelOpened(opened: TumAetUiAccordionPanel): void {
        if (this.multiExpandable()) {
            return;
        }
        for (const panel of this.panels) {
            if (panel !== opened) {
                panel.expanded.set(false);
            }
        }
    }
}

/** @internal What the accordion needs from one of its panels. */
export interface TumAetUiAccordionPanel {
    readonly expanded: { set(value: boolean): void };
}
