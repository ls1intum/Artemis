import { NgClass } from '@angular/common';
import { Component, output } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { faCheck, faPencil, faXmark } from '@fortawesome/free-solid-svg-icons';

export enum EditStateTransition {
    Edit,
    Save,
    Abort,
}

@Component({
    selector: 'jhi-edit-process',
    templateUrl: './edit-process.component.html',
    standalone: true,
    imports: [FontAwesomeModule, NgClass],
})
export class EditProcessComponent {
    editStateTransition = output<EditStateTransition>();

    protected readonly faXmark = faXmark;
    protected readonly faPencil = faPencil;
    protected readonly faCheck = faCheck;

    protected editing: boolean = false;

    onEdit() {
        this.editing = true;
        this.editStateTransition.emit(EditStateTransition.Edit);
    }

    onSave() {
        this.editing = false;
        this.editStateTransition.emit(EditStateTransition.Save);
    }

    onAbort() {
        this.editing = false;
        this.editStateTransition.emit(EditStateTransition.Abort);
    }
}
