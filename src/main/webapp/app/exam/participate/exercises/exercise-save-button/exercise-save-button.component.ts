import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFloppyDisk } from '@fortawesome/free-solid-svg-icons';
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
    submission = input<Submission>();
    save = output<void>();

    //Icons
    protected readonly faFloppyDisk = faFloppyDisk;
    protected readonly facSaveSuccess = facSaveSuccess;

    onSave() {
        this.save.emit();
    }
}
