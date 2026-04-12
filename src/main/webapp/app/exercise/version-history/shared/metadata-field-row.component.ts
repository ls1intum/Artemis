import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { faRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export interface MetadataFieldRow {
    id: string;
    label: string;
    currentDisplay: string | number;
    previousDisplay: string | number;
    currentRaw?: string | number | boolean;
    previousRaw?: string | number | boolean;
    currentEmpty: boolean;
    previousEmpty: boolean;
    revertable: boolean;
}

@Component({
    selector: 'jhi-metadata-field-row',
    template: `
        <div class="metadata-row">
            <span class="metadata-row__label">
                {{ field().label }}
                @if (isDiffView() && field().revertable) {
                    <button
                        pButton
                        type="button"
                        text
                        rounded
                        size="small"
                        class="metadata-diff__revert"
                        [pTooltip]="'artemisApp.exercise.versionHistory.revert.tooltip' | artemisTranslate: { value: field().previousDisplay }"
                        tooltipPosition="right"
                        (click)="revertField.emit({ fieldId: field().id, fieldLabel: field().label, previousRaw: field().previousRaw })"
                    >
                        <fa-icon [icon]="faRotateLeft" aria-hidden="true" />
                    </button>
                }
            </span>
            @if (isDiffView()) {
                <div class="metadata-diff">
                    <span class="metadata-diff__value metadata-diff__value--old" [class.metadata-diff__value--empty]="field().previousEmpty">
                        {{ field().previousDisplay }}
                    </span>
                    <span class="metadata-diff__arrow">&rarr;</span>
                    <span class="metadata-diff__value metadata-diff__value--new" [class.metadata-diff__value--empty]="field().currentEmpty">
                        {{ field().currentDisplay }}
                    </span>
                </div>
            } @else if (showUnsetLabel() && field().currentEmpty) {
                <span class="metadata-row__value metadata-row__value--empty" jhiTranslate="global.generic.unset"></span>
            } @else {
                <span class="metadata-row__value">{{ field().currentDisplay }}</span>
            }
        </div>
    `,
    imports: [ButtonModule, TooltipModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetadataFieldRowComponent {
    readonly field = input.required<MetadataFieldRow>();
    readonly isDiffView = input<boolean>(false);
    readonly showUnsetLabel = input<boolean>(false);

    readonly revertField = output<{ fieldId: string; fieldLabel: string; previousRaw: unknown }>();

    protected readonly faRotateLeft = faRotateLeft;
}
