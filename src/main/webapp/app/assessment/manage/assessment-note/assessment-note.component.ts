import { Component, DestroyRef, ElementRef, afterNextRender, afterRenderEffect, computed, inject, input, output, viewChild } from '@angular/core';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiInputDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-assessment-note',
    templateUrl: './assessment-note.component.html',
    styleUrls: ['./assessment-note.component.scss'],
    imports: [TranslateDirective, TumAetUiInputDirective],
})
export class AssessmentNoteComponent {
    private readonly destroyRef = inject(DestroyRef);

    readonly assessmentNote = input<AssessmentNote | undefined>();
    /** Whether the title is shown; a host that names the note elsewhere (e.g. in a tab) turns it off. */
    readonly showTitle = input(true);

    readonly onAssessmentNoteChange = output<AssessmentNote>();

    /** Same cap as a feedback description, shown to the tutor as an `n/500` counter. The column itself is unbounded. */
    protected readonly NOTE_MAX_LENGTH = 500;

    /** The host replaces the note on every input, so the length follows the text as it is typed. */
    readonly noteLength = computed(() => (this.assessmentNote()?.note ?? '').length);

    private readonly textarea = viewChild.required<ElementRef<HTMLTextAreaElement>>('noteTextarea');

    constructor() {
        // The text box grows with its text, like a feedback description. After render, since it measures the rendered box.
        afterRenderEffect(() => {
            this.assessmentNote();
            this.autogrow();
        });

        // The same text wraps into more lines once the box gets narrower, and a box in a hidden tab measures no height at all
        // until its tab opens, so the height follows the width as well. Only the width: the height written here would
        // otherwise trigger the observer again.
        afterNextRender(() => {
            const textarea = this.textarea().nativeElement;
            let lastWidth = textarea.clientWidth;
            const resizeObserver = new ResizeObserver(([entry]) => {
                if (entry.contentRect.width !== lastWidth) {
                    lastWidth = entry.contentRect.width;
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
        this.autogrow();
    }

    /** Fits the text box to its text. Collapsing it first lets the box shrink again when text is removed. */
    private autogrow(): void {
        const textarea = this.textarea().nativeElement;
        textarea.style.height = '0px';
        textarea.style.height = `${textarea.scrollHeight}px`;
    }
}
