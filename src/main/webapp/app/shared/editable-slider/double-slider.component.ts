import { Component, ElementRef, OnChanges, SimpleChanges, input, model, viewChild } from '@angular/core';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
})
export class DoubleSliderComponent implements OnChanges {
    editStateTransition = input.required<EditStateTransition>();
    currentValue = input.required<number>();
    min = input.required<number>();
    max = input.required<number>();

    currentVal: number;
    initialVal: number;

    initialValue = model.required<number>();

    currentSlider = viewChild.required<ElementRef>('currentSlider');
    parentDiv = viewChild.required<ElementRef>('parentContainer');

    onEdit() {
        this.currentSlider().nativeElement.disabled = false;
        this.parentDiv().nativeElement.classList.add('editing');
        //ensure, that current val is up-to-date.
        this.currentVal = this.currentValue();
        this.initialVal = this.initialValue();
    }

    onAbort() {
        this.currentSlider().nativeElement.disabled = true;
        this.initialValue.set(this.initialVal);
        this.currentSlider().nativeElement.value = this.currentVal;
        this.parentDiv().nativeElement.classList.remove('editing');
    }

    onTrySave() {
        this.currentSlider().nativeElement.disabled = true;
        this.initialValue.set(this.currentSlider().nativeElement.value);
    }

    onSaved() {
        this.currentVal = this.initialValue();
        this.parentDiv().nativeElement.classList.remove('editing');
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.editStateTransition) {
            switch (changes.editStateTransition.currentValue) {
                case EditStateTransition.Edit:
                    this.onEdit();
                    break;
                case EditStateTransition.Abort:
                    this.onAbort();
                    break;
                case EditStateTransition.TrySave:
                    this.onTrySave();
                    break;
                case EditStateTransition.Saved:
                    this.onSaved();
                    break;
            }
        }
    }
}
