import { Component, DestroyRef, ElementRef, afterNextRender, afterRenderEffect, inject, input, output, viewChild } from '@angular/core';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiInputDirective } from '@tumaet/ui-angular';
import { autogrowTextarea } from 'app/foundation/util/textarea-autogrow.util';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
    styleUrls: ['./assessment-note.component.scss'],
    imports: [TranslateDirective, TumAetUiInputDirective],
})
export class AssessmentNoteComponent {
    private readonly destroyRef = inject(DestroyRef);

    private readonly noteTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('noteTextarea');

    readonly assessmentNote = input<AssessmentNote | undefined>();

    readonly onAssessmentNoteChange = output<AssessmentNote>();

    constructor() {
        // Re-fit whenever the note changes, which also covers a note that is loaded after the first render.
        afterRenderEffect(() => {
            this.assessmentNote();
            this.autogrow();
        });

        // The text wraps differently when the available width changes, which no note change signals.
        // Only width changes are handled so that the height written by autogrow does not retrigger the observer.
        afterNextRender(() => {
            const textarea = this.noteTextarea()?.nativeElement;
            if (!textarea) {
                return;
            }
            let lastWidth = textarea.clientWidth;
            const resizeObserver = new ResizeObserver((entries) => {
                const width = entries[0]?.contentRect.width;
                if (width !== undefined && width !== lastWidth) {
                    lastWidth = width;
                    this.autogrow();
                }
            });
            resizeObserver.observe(textarea);
            this.destroyRef.onDestroy(() => resizeObserver.disconnect());
        });
    }

    /**
     * Called whenever an input is made on the internal tutor note text box.
     * @param event the input event containing the text of the note
     */
    onAssessmentNoteInput(event: Event) {
        const target = event.target;
        if (!(target instanceof HTMLTextAreaElement)) {
            return;
        }
        autogrowTextarea(target);
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

    private autogrow(): void {
        const textarea = this.noteTextarea()?.nativeElement;
        if (textarea) {
            autogrowTextarea(textarea);
        }
    }
}
