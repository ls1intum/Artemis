import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    TumUiEmptyComponent,
    TumUiEmptyContentComponent,
    TumUiEmptyDescriptionComponent,
    TumUiEmptyHeaderComponent,
    TumUiEmptyMediaComponent,
    TumUiEmptyTitleComponent,
    TumUiSize,
} from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * A `tum-ui-empty` with Artemis' translation keys already wired in.
 *
 * The package component is slot-only on purpose - a design system must not decide what an application says - and
 * the generation surfaces need the same eight-line composition in seven places. This is that composition, once,
 * with the two keys as inputs. It adds no styling and no structure of its own.
 *
 * The rule the package states and this component exists to make cheap: **an empty state carries an action, or it
 * names who can act.** `descriptionKey` is therefore not decoration - it is the sentence that says why the region
 * is empty and what fills it. Project the action, when there is one, as content.
 */
@Component({
    selector: 'jhi-hyperion-empty',
    template: `
        <tum-ui-empty [size]="size()">
            <tum-ui-empty-header>
                @if (icon(); as emptyIcon) {
                    <tum-ui-empty-media variant="icon"><fa-icon [icon]="emptyIcon" /></tum-ui-empty-media>
                }
                <tum-ui-empty-title>{{ titleKey() | artemisTranslate: titleParams() }}</tum-ui-empty-title>
                @if (descriptionKey(); as description) {
                    <tum-ui-empty-description>{{ description | artemisTranslate: descriptionParams() }}</tum-ui-empty-description>
                }
            </tum-ui-empty-header>
            <tum-ui-empty-content><ng-content /></tum-ui-empty-content>
        </tum-ui-empty>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        TumUiEmptyComponent,
        TumUiEmptyContentComponent,
        TumUiEmptyDescriptionComponent,
        TumUiEmptyHeaderComponent,
        TumUiEmptyMediaComponent,
        TumUiEmptyTitleComponent,
    ],
})
export class HyperionEmptyComponent {
    readonly titleKey = input.required<string>();
    readonly titleParams = input<Record<string, unknown> | undefined>();
    /** The sentence that says why the region is empty, and who or what fills it. */
    readonly descriptionKey = input<string | undefined>();
    readonly descriptionParams = input<Record<string, unknown> | undefined>();
    readonly icon = input<IconDefinition | undefined>();
    /** `small` is the docked-panel tier; `medium` is the page tier. Density is an input, never a second component. */
    readonly size = input<TumUiSize>('medium');
}
