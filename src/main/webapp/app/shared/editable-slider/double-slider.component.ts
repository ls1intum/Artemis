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

    currentVal = 0;

    initialValue = model<number>();

    initialSlider = viewChild.required<ElementRef>('initialSlider');
    currentSlider = viewChild.required<ElementRef>('currentSlider');
    parentDiv = viewChild.required<ElementRef>('parentContainer');

    ngOnInit(): void {
        this.currentVal = this.currentValue();

        console.log('abort');
        this.currentSlider().nativeElement.disabled = true;
        this.currentSlider().nativeElement.value = this.currentVal;
        this.parentDiv().nativeElement.classList.remove('editing');
    }

    ngOnChanges(changes: SimpleChanges): void {
        console.log(changes);

        switch (changes.editStateTransition.currentValue) {
            case EditStateTransition.Edit:
                console.log('edit');
                this.currentSlider().nativeElement.disabled = false;
                this.parentDiv().nativeElement.classList.add('editing');
                break;
            case EditStateTransition.Abort:
                console.log('abort');
                this.currentSlider().nativeElement.disabled = true;
                this.currentSlider().nativeElement.value = this.currentVal;
                this.parentDiv().nativeElement.classList.remove('editing');
                break;
            case EditStateTransition.Save:
                console.log('save');
                this.currentSlider().nativeElement.disabled = true;
                this.initialValue.set(this.currentSlider().nativeElement.value);
                this.currentVal = this.currentSlider().nativeElement.value;
                this.initialSlider().nativeElement.value = this.initialValue();
                this.parentDiv().nativeElement.classList.remove('editing');
                break;
        }
    }
}
