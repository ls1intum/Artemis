import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'div[jhi-statistics-span-selector]',
    imports: [ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        @for (span of spans; track span; let i = $index) {
            <label
                class="flex flex-1 cursor-pointer items-center justify-center gap-1 border-1 border-solid border-(--tumaet-ui-border-color)! px-3! py-1.5! first:rounded-s-md last:rounded-e-md focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-(--tumaet-ui-focus-color)"
                [class.active]="currentSpan() === span"
                [class]="
                    currentSpan() === span
                        ? 'bg-(--tumaet-ui-primary-color) text-(--tumaet-ui-primary-contrast-color)'
                        : 'bg-(--tumaet-ui-control-background) text-(--tumaet-ui-text-color)'
                "
            >
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
