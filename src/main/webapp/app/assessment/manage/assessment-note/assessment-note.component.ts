import { Component, input, output } from '@angular/core';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
    styleUrls: ['./assessment-note.component.scss'],
    imports: [TranslateDirective],
})
export class AssessmentNoteComponent {
    readonly assessmentNote = input<AssessmentNote | undefined>();

    readonly onAssessmentNoteChange = output<AssessmentNote>();

    /**
     * Called whenever an input is made on the internal tutor note text box.
     * @param event the input event containing the text of the note
     */
    onAssessmentNoteInput(event: Event) {
        const target = event.target;
        if (!(target instanceof HTMLTextAreaElement)) {
            return;
        }
        const value = target.value;
        const currentNote = this.assessmentNote();

        const updatedNote = new AssessmentNote();
        if (currentNote) {
            updatedNote.id = currentNote.id;
            updatedNote.creator = currentNote.creator;
            updatedNote.createdDate = currentNote.createdDate;
            updatedNote.lastUpdatedDate = currentNote.lastUpdatedDate;
        }
        updatedNote.note = value;

        this.onAssessmentNoteChange.emit(updatedNote);
    }
}
