import { ChangeDetectionStrategy, Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiCheckboxComponent } from 'app/shared-ui/tum-ui/checkbox/tum-ui-checkbox.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

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
    /** Emits the edited group on save (only when something actually changed); cancel/close emit nothing. */
    readonly saved = output<ExerciseGroup>();

    readonly draftTitle = signal('');
    readonly draftIsMandatory = signal(true);

    readonly isTitleValid = computed(() => {
        const title = this.draftTitle().trim();
        return title.length > 0 && title.length <= MAX_TITLE_LENGTH;
    });

    constructor() {
        effect(() => {
            const g = this.group();
            this.draftTitle.set(g.title ?? '');
            this.draftIsMandatory.set(g.isMandatory ?? true);
        });
    }

    onSave(): void {
        const g = this.group();
        const updated: ExerciseGroup = { ...g, title: this.draftTitle().trim(), isMandatory: this.draftIsMandatory() };
        if (updated.title !== (g.title ?? '') || updated.isMandatory !== (g.isMandatory ?? true)) {
            this.saved.emit(updated);
        }
        this.visible.set(false);
    }

    onCancel(): void {
        this.visible.set(false);
    }
}
