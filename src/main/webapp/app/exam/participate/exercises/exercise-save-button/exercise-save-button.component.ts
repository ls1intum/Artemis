import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFloppyDisk, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { facSaveSuccess } from '../../../../../content/icons/icons';
import { Submission } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-exercise-save-button',
    templateUrl: './exercise-save-button.component.html',
    styleUrls: ['./exercise-save-button.component.scss'],
    standalone: true,
    imports: [FaIconComponent],
})
export class ExerciseSaveButtonComponent {
    @Input() submission?: Submission;
    @Output() save = new EventEmitter<void>();

    //Icons
    readonly faFloppyDisk = faFloppyDisk;
    readonly faListAlt = faListAlt;
    readonly facSaveSuccess = facSaveSuccess;

    onSave() {
        if (this.submission) {
            this.submission.submitted = true;
        }
        this.save.emit();
    }
}
