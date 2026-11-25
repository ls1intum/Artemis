import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiSelectButtonComponent } from '@tumaet/ui-angular';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

interface SpanOption {
    label: string;
    value: SpanType;
}

@Component({
    selector: 'div[jhi-statistics-span-selector]',
    imports: [ArtemisTranslatePipe, FormsModule, TumAetUiSelectButtonComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: `
        .span-selector-scroll {
            max-width: 100%;
            overflow-x: auto;
            /* Keep the focus outline of the first and last period inside the scroll container. */
            padding: 3px;
            margin: -3px;
        }
    `,
    template: `
        <div class="span-selector-scroll">
            <tumaet-ui-select-button
                [attr.aria-label]="'statistics.spanLabel' | artemisTranslate"
                [options]="spanOptions"
                [ngModel]="currentSpan()"
                (ngModelChange)="spanChange.emit($event)"
                optionLabel="label"
                optionValue="value"
                size="small"
                [allowEmpty]="false"
                [itemTemplate]="spanItem"
            />
        </div>
        <ng-template #spanItem let-option>
            {{ option.label | artemisTranslate }}
        </ng-template>
    `,
})
export class StatisticsSpanSelectorComponent {
    readonly currentSpan = input.required<SpanType>();
    readonly spanChange = output<SpanType>();
    protected readonly spanOptions: SpanOption[] = [
        { label: 'statistics.span.day', value: SpanType.DAY },
        { label: 'statistics.span.week', value: SpanType.WEEK },
        { label: 'statistics.span.month', value: SpanType.MONTH },
        { label: 'statistics.span.quarter', value: SpanType.QUARTER },
        { label: 'statistics.span.year', value: SpanType.YEAR },
    ];
}
