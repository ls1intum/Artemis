import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The Athena mark, shown next to the Athena AI Feedback title on the course overview and in the onboarding wizard.
 *
 * Decorative: the title it sits next to already names the feature, so the image is hidden from screen readers rather
 * than announced twice.
 *
 * The artwork is a single monochrome black PNG on a transparent background. The dark theme inverts it rather than
 * shipping a second, white copy: there is no colour for `invert` to distort, and it leaves the alpha channel alone,
 * so the figure turns white and its background stays transparent.
 */
@Component({
    selector: 'jhi-athena-logo',
    template: `<img class="athena-logo" src="public/images/athena/athena-logo.png" alt="" aria-hidden="true" [style.height.px]="size()" />`,
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: [
        `
            :host {
                display: contents;
            }

            .athena-logo {
                width: auto;
            }

            :host-context(html[prime-ng-use-dark-theme='true']) .athena-logo {
                filter: invert(1);
            }
        `,
    ],
})
export class AthenaLogoComponent {
    /**
     * The height of the mark in pixels. Larger than the Iris logo next to it by default, because this is a full figure
     * whose spear, shield and helmet detail turns to mush at the ~21px the Iris mark uses.
     */
    readonly size = input<number>(28);
}
