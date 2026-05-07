import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

const DEFAULT_STEP = 1;
const BASE_LABEL_MARGIN = 0.4;

/**
 * By trial and error it was found out that the slider thumbs are moving on
 * 97% of the width compared to the colored bar that is displayed between the two thumbs.
 *
 * This issue is resolved with this factor when multiplied to {@link sliderMinPercentage} and {@link sliderMaxPercentage}
 * to calculate the position of the label, as it is not the exact same position as the thumbs.
 */
const SLIDER_THUMB_LABEL_POSITION_ADJUSTMENT_FACTOR = 0.97;

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    imports: [FormsModule, ReactiveFormsModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RangeSliderComponent {
    readonly generalMaxValue = input.required<number>();
    readonly generalMinValue = input.required<number>();
    readonly step = input<number>(DEFAULT_STEP);
    readonly labelSymbol = input<'%' | undefined>(undefined);

    readonly selectedMinValue = input.required<number>();
    readonly selectedMaxValue = input.required<number>();

    readonly selectedMinValueChange = output<number>();
    readonly selectedMaxValueChange = output<number>();

    /**
     * Local mirrors of the selected values driven by the slider's `ngModel` bindings. These track the
     * dragging state and only flow back to the parent on the `(change)` event (mouse-up), preserving
     * the original behaviour where the parent was not notified on every drag tick.
     */
    protected readonly localMinValue = signal<number>(0);
    protected readonly localMaxValue = signal<number>(0);

    protected readonly SLIDER_THUMB_LABEL_POSITION_ADJUSTMENT_FACTOR = SLIDER_THUMB_LABEL_POSITION_ADJUSTMENT_FACTOR;

    protected readonly valueRange = computed(() => this.generalMaxValue() - this.generalMinValue());

    /** Margin to the labels considering the adjustments needed by the added {@link labelSymbol} */
    protected readonly labelMargin = computed(() => BASE_LABEL_MARGIN - BASE_LABEL_MARGIN * (this.labelSymbol()?.length ?? 0));

    protected readonly sliderMinPercentage = computed(() => {
        const minSelection = this.localMinValue() >= this.localMaxValue() ? this.localMaxValue() - this.step() : this.localMinValue();
        return ((minSelection - this.generalMinValue()) / this.valueRange()) * 100;
    });

    protected readonly sliderMaxPercentage = computed(() => {
        const maxSelection = this.localMaxValue() <= this.localMinValue() ? this.localMinValue() + this.step() : this.localMaxValue();
        return 100 - ((maxSelection - this.generalMinValue()) / this.valueRange()) * 100;
    });

    constructor() {
        // Sync the parent-supplied selected values into the local mirrors whenever the inputs change.
        effect(() => this.localMinValue.set(this.selectedMinValue()));
        effect(() => this.localMaxValue.set(this.selectedMaxValue()));
    }

    /** Clamps the local mirror values during dragging without emitting an event to the parent. */
    onSelectedValueDuringDrag(event: Event): void {
        this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
    }

    onSelectedMinValueChanged(event: Event): void {
        const updatedMinValue = this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        this.selectedMinValueChange.emit(updatedMinValue);
    }

    onSelectedMaxValueChanged(event: Event): void {
        const updatedMaxValue = this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
        this.selectedMaxValueChange.emit(updatedMaxValue);
    }

    private ensureMinValueIsSmallerThanMaxValueViceVersa(event: Event): number {
        const inputEl = event.target as HTMLInputElement;
        const minSliderIsUpdated = inputEl.className.includes('range-min');

        if (minSliderIsUpdated) {
            if (this.localMinValue() >= this.localMaxValue()) {
                this.localMinValue.set(this.localMaxValue() - this.step());
            }
            return this.localMinValue();
        }

        if (this.localMaxValue() <= this.localMinValue()) {
            this.localMaxValue.set(this.localMinValue() + this.step());
        }
        return this.localMaxValue();
    }
}
