import { Component, EventEmitter, Output } from '@angular/core';
import { AssessmentNote } from 'app/entities/assessment-note.model';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
})
export class AssessmentNoteComponent {
    assessmentNote: AssessmentNote = new AssessmentNote();
    @Output() onAssessmentNoteChange = new EventEmitter<AssessmentNote>();

    /**
     * Called whenever an input is made on the internal tutor note text box.
     */
    onAssessmentNoteInput(event: any) {
        this.assessmentNote.note = event.target.value;
        this.onAssessmentNoteChange.emit(this.assessmentNote);
    }

    /**
     * Return an empty string if the assessment note or its note field is undefined, or otherwise the text of the note.
     */
    getAssessmentNoteText() {
        if (this.assessmentNote !== undefined && this.assessmentNote.note !== undefined) {
            return this.assessmentNote.note;
        } else {
            return '';
        }
    }
}
