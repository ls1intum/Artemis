import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

const DEFAULT_STEP = 1;

@Component({
    selector: 'jhi-range-slider',
    templateUrl: './range-slider.component.html',
    styleUrls: ['./range-slider.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule],
})
export class RangeSliderComponent implements OnInit, OnDestroy {
    @Input() generalMaxValue: number;
    @Input() generalMinValue: number;
    @Input() step: number = DEFAULT_STEP;

    /** When extending the supported label symbols you might have to adjust the logic for */
    @Input() labelSymbol?: '%';

    @Input() selectedMinValue: number;
    @Input() selectedMaxValue: number;
    @Output() selectedMinValueChange: EventEmitter<number> = new EventEmitter<number>();
    @Output() selectedMaxValueChange: EventEmitter<number> = new EventEmitter<number>();

    rangeInputElements?: NodeList;
    eventListeners: { element: HTMLInputElement; listener: (event: Event) => void }[] = [];

    get sliderMinPercentage(): number {
        return ((this.selectedMinValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    get sliderMaxPercentage(): number {
        return 100 - ((this.selectedMaxValue - this.generalMinValue) / (this.generalMaxValue - this.generalMinValue)) * 100;
    }

    /** Ensures that the label is placed centered underneath the range thumb */
    LABEL_MARGIN = 0.4;

    /**
     * By trial and error it was found out that the slider thumbs are moving on
     * 97% of the width compared to the colored bar that is displayed between the two thumbs.
     *
     * This issue is resolved with this factor when multiplied to {@link sliderMinPercentage} and {@link sliderMaxPercentage}
     * to calculate the position of the label, as it is not the exact same position as the thumbs.
     *
     * <i>
     * To reproduce:
     * If you inspect the progress bar in the initial state you will see that it is 100% wide and ends at the left end of
     * the minimum range thumb.
     * However, if you move the minimum thumb to the right (as far as possible), you will notice that the progress bar
     * ends at the right end of the range thumb. - This is the problem that we address with this factor.</i>
     */
    SLIDER_THUMB_LABEL_POSITION_ADJUSTMENT_FACTOR = 0.97;

    constructor(private elRef: ElementRef) {}

    ngOnInit() {
        this.rangeInputElements = this.elRef.nativeElement.querySelectorAll('.range-input input');

        this.rangeInputElements?.forEach((input: HTMLInputElement) => {
            const listener = (event: InputEvent) => {
                this.ensureMinValueIsSmallerThanMaxValueViceVersa(event);
            };
            input.addEventListener('input', listener);
            this.eventListeners.push({ element: input, listener });
        });

        this.LABEL_MARGIN = this.getLabelMargin();
    }

    ngOnDestroy() {
        this.eventListeners.forEach(({ element, listener }) => {
            element.removeEventListener('input', listener);
        });
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
        const input = event.target as HTMLInputElement;
        const minSliderIsUpdated = input.className.includes('range-min');

        if (minSliderIsUpdated) {
            if (this.selectedMinValue >= this.selectedMaxValue) {
                this.selectedMinValue = this.selectedMaxValue - this.step;
            }
            return this.selectedMinValue;
        }

        if (this.selectedMaxValue <= this.selectedMinValue) {
            this.selectedMaxValue = this.selectedMinValue + this.step;
        }
        return this.selectedMaxValue;
    }

    /**
     * @return margin to labels considering the adjustments needed by the added {@link labelSymbol}
     */
    private getLabelMargin() {
        const BASE_LABEL_MARGIN = 0.4; // should be approximately the width of 1 symbol
        const shiftToTheLeftDueToAddedSymbols = BASE_LABEL_MARGIN * (this.labelSymbol?.length ?? 0);

        return BASE_LABEL_MARGIN - shiftToTheLeftDueToAddedSymbols;
    }
}
