import { Component, input, model } from '@angular/core';
import { EditProcessComponent, EditStateTransition } from 'app/shared/editable-slider/edit-process.component';
import { DoubleSliderComponent } from 'app/shared/editable-slider/double-slider.component';

@Component({
    selector: 'jhi-editable-slider',
    templateUrl: './editable-slider.component.html',
    standalone: true,
    imports: [EditProcessComponent, DoubleSliderComponent],
})
export class EditableSliderComponent {
    readonly title = input.required<string>();
    readonly currentValue = input.required<number>();
    readonly min = input.required<number>();
    readonly max = input.required<number>();
    readonly disabled = input<boolean>(true);
    initialValue = model.required<number>();
    editStateTransition = model<EditStateTransition>(EditStateTransition.Abort);
}
