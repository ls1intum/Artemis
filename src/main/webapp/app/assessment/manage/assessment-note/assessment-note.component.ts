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
    readonly assessmentNote = input<AssessmentNote | undefined, AssessmentNote | undefined>(undefined, {
        transform: (value: AssessmentNote | undefined) => value ?? new AssessmentNote(),
    });

    readonly onAssessmentNoteChange = output<AssessmentNote>();

    /**
     * Called whenever an input is made on the internal tutor note text box.
     * @param event the input event containing the text of the note
     */
    onAssessmentNoteInput(event: Event) {
        const note = this.assessmentNote();
        if (note) {
            note.note = (event.target as HTMLTextAreaElement).value;
            this.onAssessmentNoteChange.emit(note);
        }
    }
}
