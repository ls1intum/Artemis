import { ChangeDetectionStrategy, Component, effect, inject, input, model, output } from '@angular/core';
import { faBan, faPencil, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, KnowledgeAreaDTO, KnowledgeAreaValidators } from 'app/atlas/shared/entities/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

/**
 * Form structure for knowledge area editing.
 */
interface KnowledgeAreaForm {
    title: FormControl<string | undefined>;
    shortTitle: FormControl<string | undefined>;
    description: FormControl<string | undefined>;
    parentId: FormControl<number | undefined>;
}

/**
 * Component for editing knowledge areas.
 * Provides a form for creating and updating knowledge area details with hierarchy support.
 */
@Component({
    selector: 'jhi-knowledge-area-edit',
    templateUrl: './knowledge-area-edit.component.html',
    imports: [TranslateDirective, ButtonComponent, DeleteButtonDirective, FaIconComponent, FormsModule, ReactiveFormsModule, MarkdownEditorMonacoComponent, HtmlForMarkdownPipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KnowledgeAreaEditComponent {
    private readonly formBuilder = inject(FormBuilder);

    /** Available knowledge areas for parent selection */
    readonly knowledgeAreas = input<KnowledgeArea[]>([]);

    /** The knowledge area being edited (required) */
    readonly knowledgeArea = input.required<KnowledgeAreaDTO>();

    /** Whether the form is in editing mode (two-way binding) */
    readonly isEditing = model<boolean>(false);

    /** Observable for dialog error messages */
    readonly dialogError = input<Observable<string>>();

    /** Emitted when the knowledge area is saved */
    readonly onSave = output<KnowledgeAreaDTO>();

    /** Emitted when the knowledge area should be deleted */
    readonly onDelete = output<number>();

    /** Emitted when a new competency should be created under this knowledge area */
    readonly onOpenNewCompetency = output<number>();

    /** Emitted when a new child knowledge area should be created */
    readonly onOpenNewKnowledgeArea = output<number>();

    /** Emitted when the edit panel should be closed */
    readonly onClose = output<void>();

    /** The reactive form for editing knowledge area properties */
    form: FormGroup<KnowledgeAreaForm>;

    /** Icons */
    protected readonly faPencil = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faPlus = faPlus;

    /** Constants */
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = KnowledgeAreaValidators;

    constructor() {
        // Effect to initialize/update form when knowledgeArea input changes
        effect(() => {
            const ka = this.knowledgeArea();
            this.initializeForm(ka);
        });

        // Effect to enable/disable form based on editing state
        effect(() => {
            const editing = this.isEditing();
            if (this.form) {
                if (editing) {
                    this.form.enable();
                } else {
                    this.form.disable();
                }
            }
        });
    }

    /**
     * Initializes the form with the given knowledge area data.
     * @param knowledgeArea - The knowledge area to populate the form with
     */
    private initializeForm(knowledgeArea: KnowledgeAreaDTO): void {
        this.form = this.formBuilder.nonNullable.group({
            title: [knowledgeArea.title, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.TITLE_MAX)]],
            shortTitle: [knowledgeArea.shortTitle, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.SHORT_TITLE_MAX)]],
            description: [knowledgeArea.description, [Validators.maxLength(KnowledgeAreaValidators.DESCRIPTION_MAX)]],
            parentId: [knowledgeArea.parentId, [this.createNoCircularDependencyValidator(knowledgeArea)]],
        });

        // Apply initial editing state
        if (!this.isEditing()) {
            this.form.disable();
        }
    }

    /**
     * Saves the knowledge area with current form values.
     */
    save(): void {
        const updatedValues = this.form.getRawValue();
        const updatedKnowledgeArea: KnowledgeAreaDTO = { ...this.knowledgeArea(), ...updatedValues };
        this.isEditing.set(false);
        this.onSave.emit(updatedKnowledgeArea);
    }

    /**
     * Emits delete event for the current knowledge area.
     */
    delete(): void {
        const id = this.knowledgeArea().id;
        if (id !== undefined) {
            this.onDelete.emit(id);
        }
    }

    /**
     * Opens the panel to create a new competency under this knowledge area.
     */
    openNewCompetency(): void {
        const id = this.knowledgeArea().id;
        if (id !== undefined) {
            this.onOpenNewCompetency.emit(id);
        }
    }

    /**
     * Opens the panel to create a new child knowledge area.
     */
    openNewKnowledgeArea(): void {
        const id = this.knowledgeArea().id;
        if (id !== undefined) {
            this.onOpenNewKnowledgeArea.emit(id);
        }
    }

    /**
     * Closes the edit panel.
     */
    close(): void {
        this.onClose.emit();
    }

    /**
     * Enables editing mode.
     */
    edit(): void {
        this.isEditing.set(true);
    }

    /**
     * Cancels editing and resets the form.
     * If creating a new knowledge area, closes the panel.
     */
    cancel(): void {
        this.form.reset();
        this.isEditing.set(false);

        // Canceling when creating a new knowledge area closes it
        if (this.knowledgeArea().id === undefined) {
            this.onClose.emit();
        }
    }

    /**
     * Updates description form control on markdown change.
     * @param content - The new markdown content
     */
    updateDescriptionControl(content: string): void {
        this.form.controls.description.setValue(content);
        this.form.controls.description.markAsDirty();
    }

    /**
     * Creates a validator that verifies that updating a knowledge area cannot lead to circular dependencies.
     * (I.e. the new parent of a knowledge area must not be itself or one of its current descendants)
     * @param knowledgeArea - The knowledge area being validated
     */
    private createNoCircularDependencyValidator(knowledgeArea: KnowledgeAreaDTO) {
        // If the knowledgeArea is new, no validator is needed
        if (knowledgeArea.id === undefined) {
            return (_parentIdControl: FormControl<number | undefined>) => null;
        }
        return (parentIdControl: FormControl<number | undefined>) => {
            if (parentIdControl.value === undefined) {
                return null;
            }
            if (this.selfOrDescendantsHaveId(knowledgeArea, parentIdControl.value)) {
                return {
                    circularDependency: true,
                };
            }
            return null;
        };
    }

    /**
     * Checks if the given knowledge area or one of its descendants have the given id.
     * @param knowledgeArea - The knowledge area to check
     * @param id - The id to check for
     */
    private selfOrDescendantsHaveId(knowledgeArea: KnowledgeAreaDTO, id: number): boolean {
        if (knowledgeArea.id === id) {
            return true;
        }

        for (const child of knowledgeArea.children ?? []) {
            if (this.selfOrDescendantsHaveId(child, id)) {
                return true;
            }
        }
        return false;
    }
}
