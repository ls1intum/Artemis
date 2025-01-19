import { Component, ElementRef, OnChanges, OnInit, SimpleChanges, input, model, viewChild } from '@angular/core';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    standalone: true,
})
export class DoubleSliderComponent implements OnChanges, OnInit {
    readonly editStateTransition = input.required<EditStateTransition>();
    readonly currentValue = input.required<number>();
    readonly min = input.required<number>();
    readonly max = input.required<number>();

    currentVal: number;
    initialVal: number;

    initialValue = model.required<number>();

    initialSlider = viewChild.required<ElementRef>('initialSlider');
    currentSlider = viewChild.required<ElementRef>('currentSlider');
    parentDiv = viewChild.required<ElementRef>('parentContainer');

    ngOnInit(): void {
        this.currentSlider().nativeElement.disabled = true;
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.editStateTransition) {
            console.log(changes.editStateTransition.currentValue);

            switch (changes.editStateTransition.currentValue) {
                case EditStateTransition.Edit:
                    this.currentSlider().nativeElement.disabled = false;
                    this.parentDiv().nativeElement.classList.add('editing');
                    //ensure, that current val is up-to-date.
                    this.currentVal = this.currentValue();
                    this.initialVal = this.initialValue();
                    break;
                case EditStateTransition.Abort:
                    console.log('this.current ' + this.currentVal);
                    this.currentSlider().nativeElement.disabled = true;
                    this.initialValue.set(this.initialVal);
                    this.currentSlider().nativeElement.value = this.currentVal;
                    this.parentDiv().nativeElement.classList.remove('editing');
                    break;
                case EditStateTransition.TrySave:
                    this.currentSlider().nativeElement.disabled = true;
                    this.initialValue.set(this.currentSlider().nativeElement.value);
                    break;
                case EditStateTransition.Saved:
                    this.currentVal = this.initialValue();
                    this.parentDiv().nativeElement.classList.remove('editing');
                    break;
            }
        }
    }
}
