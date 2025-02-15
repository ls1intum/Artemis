import { NgClass } from '@angular/common';
import { Component, OnChanges, SimpleChanges, input, model } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { faCheck, faPencil, faXmark } from '@fortawesome/free-solid-svg-icons';

export enum EditStateTransition {
    Edit,
    TrySave,
    Saved,
    Abort,
}

@Component({
    selector: 'jhi-edit-process',
    templateUrl: './edit-process.component.html',
    styleUrls: ['./edit-process.component.scss'],
    standalone: true,
    imports: [FontAwesomeModule, NgClass],
})
export class EditProcessComponent implements OnChanges {
    editStateTransition = model<EditStateTransition>();
    disabled = input<boolean>(false);

    protected readonly faXmark = faXmark;
    protected readonly faPencil = faPencil;
    protected readonly faCheck = faCheck;

    protected editing: boolean = false;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.editStateTransition) {
            switch (changes.editStateTransition.currentValue) {
                case EditStateTransition.Edit:
                    this.editing = true;
                    break;
                case EditStateTransition.TrySave:
                case EditStateTransition.Abort:
                default:
                    this.editing = false;
                    break;
            }
        }
    }

    onEdit() {
        if (!this.disabled()) {
            this.editStateTransition.set(EditStateTransition.Edit);
        }
    }

    onSave() {
        if (!this.disabled()) {
            this.editStateTransition.set(EditStateTransition.TrySave);
        }
    }

    onAbort() {
        if (!this.disabled()) {
            this.editStateTransition.set(EditStateTransition.Abort);
        }
    }
}
