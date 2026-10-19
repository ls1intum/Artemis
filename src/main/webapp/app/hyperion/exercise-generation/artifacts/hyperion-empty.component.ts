import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TumAetUiEmptyStateComponent } from '@tumaet/ui-angular';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Empty generation output with a reason and optional recovery action. */
@Component({
    selector: 'jhi-hyperion-empty',
    template: `
        <tumaet-ui-empty-state
            class="p-6"
            variant="plain"
            [icon]="icon()"
            [title]="titleKey() | artemisTranslate: titleParams()"
            [description]="descriptionKey() | artemisTranslate: descriptionParams()"
        >
            <ng-container tumAetUiEmptyStateActions><ng-content /></ng-container>
        </tumaet-ui-empty-state>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TumAetUiEmptyStateComponent],
})
export class HyperionEmptyComponent {
    readonly titleKey = input.required<string>();
    readonly titleParams = input<Record<string, unknown> | undefined>();
    readonly descriptionKey = input<string | undefined>();
    readonly descriptionParams = input<Record<string, unknown> | undefined>();
    readonly icon = input<IconDefinition | undefined>();
}
