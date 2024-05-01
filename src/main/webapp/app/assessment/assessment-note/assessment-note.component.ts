import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AssessmentNote } from 'app/entities/assessment-note.model';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
    styleUrls: ['./assessment-note.component.scss'],
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

    get assessmentNote(): AssessmentNote {
        return this._assessmentNote;
    }

    /**
     * Called whenever an input is made on the internal tutor note text box.
     * @param event the input event containing the text of the note
     */
    onAssessmentNoteInput(event: any) {
        this._assessmentNote.note = event.target.value;
        this.onAssessmentNoteChange.emit(this._assessmentNote);
    }
}
