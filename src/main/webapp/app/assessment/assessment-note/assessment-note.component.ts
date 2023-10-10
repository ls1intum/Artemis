import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AssessmentNote } from 'app/entities/assessment-note.model';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
})
export class AssessmentNoteComponent {
    private _assessmentNote: AssessmentNote;
    @Output() onAssessmentNoteChange = new EventEmitter<AssessmentNote>();

    @Input() set assessmentNote(assessmentNote: AssessmentNote | undefined) {
        if (assessmentNote === undefined) {
            this._assessmentNote = new AssessmentNote();
        } else {
            this._assessmentNote = assessmentNote;
        }
    }

    /**
     * Called whenever an input is made on the internal tutor note text box.
     */
    onAssessmentNoteInput(event: any) {
        this._assessmentNote.note = event.target.value;
        this.onAssessmentNoteChange.emit(this._assessmentNote);
    }

    /**
     * Return an empty string if the assessment note or its note field is undefined, or otherwise the text of the note.
     */
    getAssessmentNoteText() {
        if (this._assessmentNote !== undefined && this._assessmentNote.note !== undefined) {
            return this._assessmentNote.note;
        } else {
            return '';
        }
    }
}
