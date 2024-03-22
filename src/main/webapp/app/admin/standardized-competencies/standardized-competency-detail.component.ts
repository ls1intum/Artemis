import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faBan, faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
})
export class StandardizedCompetencyDetailComponent {
    @Input({ required: true }) competency: StandardizedCompetency;

    isInEditMode = false;
    copy: StandardizedCompetency;

    //icons
    readonly faEdit = faEdit;
    readonly faTrash = faTrash;
    readonly faBan = faBan;
    readonly faSave = faSave;

    @Output() onSave: EventEmitter<StandardizedCompetency> = new EventEmitter<StandardizedCompetency>();

    @Output() onDelete: EventEmitter<number> = new EventEmitter<number>();
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    delete() {
        this.onDelete.emit(this.competency.id);
    }

    edit() {
        this.isInEditMode = true;
        this.copy = Object.assign({}, this.competency);
    }

    cancel() {
        this.isInEditMode = false;
    }

    save() {
        this.isInEditMode = false;
        //TODO: see if i need to change competency to copy here.
        this.onSave.emit(this.copy);
    }
}
