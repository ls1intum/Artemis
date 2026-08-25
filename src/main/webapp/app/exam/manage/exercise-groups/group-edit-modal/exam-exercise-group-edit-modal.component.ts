import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent, TumUiInputDirective } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

/** Mirrors the server's @Size(max = 255) constraint and the varchar(255) title column. */
const MAX_TITLE_LENGTH = 255;

/**
 * Declarative title/mandatory-only edit dialog for an exam {@link ExerciseGroup}. Unlike the course-side
 * {@code ExerciseGroupEditModalComponent} (which also edits a timeline and a points cap), exam exercise groups carry
 * neither: their exercises hold their own dates and points, and the group's only editable fields are its title and
 * whether it is mandatory, so the dialog is deliberately smaller.
 */
@Component({
    selector: 'jhi-exam-exercise-group-edit-modal',
    templateUrl: './exam-exercise-group-edit-modal.component.html',
    imports: [FormsModule, TumUiDialogComponent, TumUiInputDirective, TumUiButtonComponent, TumUiCheckboxComponent, ArtemisTranslatePipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExamExerciseGroupEditModalComponent {
    protected readonly MAX_TITLE_LENGTH = MAX_TITLE_LENGTH;

    /** Two-way visibility, driven by the parent. */
    readonly visible = model<boolean>(false);
    /** The group being edited, supplied by the parent. */
    readonly group = input.required<ExerciseGroup>();
    /** Whether the parent opened the dialog on a blank draft, which only changes the dialog's header. */
    readonly isNew = input<boolean>(false);
    /** Emits the edited group on save (only when something actually changed); cancel/close emit nothing. */
    readonly saved = output<ExerciseGroup>();

    readonly draftTitle = signal('');
    readonly draftIsMandatory = signal(true);

    protected readonly headerKey = computed(() => (this.isNew() ? 'artemisApp.examManagement.exerciseGroup.create' : 'artemisApp.examManagement.exerciseGroup.update'));

    readonly isTitleValid = computed(() => {
        const title = this.draftTitle().trim();
        return title.length > 0 && title.length <= MAX_TITLE_LENGTH;
    });

    constructor() {
        // Re-seed on every open, not just when the group input changes: re-opening the *same* group after a cancelled
        // edit keeps the identical object reference, so a group-only effect would leave the discarded draft in place.
        effect(() => {
            if (!this.visible()) {
                return;
            }
            const g = this.group();
            this.draftTitle.set(g.title ?? '');
            this.draftIsMandatory.set(g.isMandatory ?? true);
        });
    }

    onSave(): void {
        const g = this.group();
        const updated = deepClone(g);
        updated.title = this.draftTitle().trim();
        updated.isMandatory = this.draftIsMandatory();
        if (updated.title !== (g.title ?? '') || updated.isMandatory !== (g.isMandatory ?? true)) {
            this.saved.emit(updated);
        }
        this.visible.set(false);
    }

    onCancel(): void {
        this.visible.set(false);
    }
}
