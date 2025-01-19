import { Component, ElementRef, OnChanges, OnInit, SimpleChanges, input, model, viewChild } from '@angular/core';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';

@Component({
    selector: 'jhi-double-slider',
    templateUrl: './double-slider.component.html',
    styleUrls: ['./double-slider.component.scss'],
    standalone: true,
})
export class DoubleSliderComponent implements OnChanges, OnInit {
    editStateTransition = input.required<EditStateTransition>();
    currentValue = input.required<number>();
    min = input.required<number>();
    max = input.required<number>();

    currentVal: number;
    initialVal: number;

    initialValue = model.required<number>();

    currentSlider = viewChild.required<ElementRef>('currentSlider');
    parentDiv = viewChild.required<ElementRef>('parentContainer');

    ngOnInit(): void {
        this.currentSlider().nativeElement.disabled = true;
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.editStateTransition) {
            switch (changes.editStateTransition.currentValue) {
                case EditStateTransition.Edit:
                    this.currentSlider().nativeElement.disabled = false;
                    this.parentDiv().nativeElement.classList.add('editing');
                    //ensure, that current val is up-to-date.
                    this.currentVal = this.currentValue();
                    this.initialVal = this.initialValue();
                    break;
                case EditStateTransition.Abort:
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
