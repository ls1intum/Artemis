import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'div[jhi-statistics-span-selector]',
    imports: [ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        @for (span of spans; track span; let i = $index) {
            <label class="btn btn-secondary" [class.active]="currentSpan() === span">
                <input type="radio" name="options" [id]="'option' + (i + 1)" [checked]="currentSpan() === span" (click)="spanChange.emit(span)" />{{
                    'statistics.span.' + span.toLowerCase() | artemisTranslate
                }}
            </label>
        }
    `,
})
export class StatisticsSpanSelectorComponent {
    readonly currentSpan = input.required<SpanType>();
    readonly spanChange = output<SpanType>();
    protected readonly spans = [SpanType.DAY, SpanType.WEEK, SpanType.MONTH, SpanType.QUARTER, SpanType.YEAR];
}
