import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Empty generation output with a reason and optional recovery action. */
@Component({
    selector: 'jhi-hyperion-empty',
    template: `
        <div class="flex flex-col items-center gap-3 text-center" [class.p-4]="size() === 'small'" [class.p-6]="size() === 'medium'">
            @if (icon(); as emptyIcon) {
                <fa-icon class="text-muted-color" [icon]="emptyIcon" />
            }
            <p class="m-0 font-medium">{{ titleKey() | artemisTranslate: titleParams() }}</p>
            @if (descriptionKey(); as description) {
                <p class="m-0 max-w-prose text-sm text-muted-color">{{ description | artemisTranslate: descriptionParams() }}</p>
            }
            <div class="flex flex-wrap justify-center gap-2"><ng-content /></div>
        </div>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
})
export class HyperionEmptyComponent {
    readonly titleKey = input.required<string>();
    readonly titleParams = input<Record<string, unknown> | undefined>();
    readonly descriptionKey = input<string | undefined>();
    readonly descriptionParams = input<Record<string, unknown> | undefined>();
    readonly icon = input<IconDefinition | undefined>();
    readonly size = input<'small' | 'medium'>('medium');
}
