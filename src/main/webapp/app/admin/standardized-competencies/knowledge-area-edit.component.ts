import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { faBan, faPencil, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, KnowledgeAreaDTO, KnowledgeAreaValidators } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-knowledge-area-edit',
    templateUrl: './knowledge-area-edit.component.html',
})
export class KnowledgeAreaEditComponent {
    private formBuilder = inject(FormBuilder);

    // values for the knowledge area select
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input({ required: true }) set knowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        this._knowledgeArea = knowledgeArea;
        this.form = this.formBuilder.nonNullable.group({
            title: [knowledgeArea.title, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.TITLE_MAX)]],
            shortTitle: [knowledgeArea.shortTitle, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.SHORT_TITLE_MAX)]],
            description: [knowledgeArea.description, [Validators.maxLength(KnowledgeAreaValidators.DESCRIPTION_MAX)]],
            parentId: [knowledgeArea.parentId, [this.createNoCircularDependencyValidator()]],
        });
        if (!this.isEditing) {
            this.form.disable();
        }
    }

    get knowledgeArea() {
        return this._knowledgeArea;
    }

    @Input() set isEditing(isEditing: boolean) {
        this._isEditing = isEditing;
        this.isEditingChange.emit(isEditing);
        if (isEditing) {
            this.form.enable();
        } else {
            this.form.disable();
        }
    }

    get isEditing() {
        return this._isEditing;
    }

    @Input() dialogError: Observable<string>;

    @Output() onSave = new EventEmitter<KnowledgeAreaDTO>();
    @Output() onDelete = new EventEmitter<number>();
    @Output() onOpenNewCompetency = new EventEmitter<number>();
    @Output() onOpenNewKnowledgeArea = new EventEmitter<number>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isEditingChange = new EventEmitter<boolean>();

    private _isEditing: boolean;
    private _knowledgeArea: KnowledgeAreaDTO;
    form: FormGroup<{
        title: FormControl<string | undefined>;
        shortTitle: FormControl<string | undefined>;
        description: FormControl<string | undefined>;
        parentId: FormControl<number | undefined>;
    }>;

    // icons
    readonly faPencil = faPencil;
    readonly faTrash = faTrash;
    readonly faBan = faBan;
    readonly faSave = faSave;
    readonly faPlus = faPlus;
    // other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = KnowledgeAreaValidators;

    save() {
        const updatedValues = this.form.getRawValue();
        const updatedKnowledgeArea: KnowledgeAreaDTO = { ...this.knowledgeArea, ...updatedValues };
        this.isEditing = false;
        this.onSave.emit(updatedKnowledgeArea);
    }

    delete() {
        this.onDelete.emit(this.knowledgeArea.id);
    }

    openNewCompetency() {
        this.onOpenNewCompetency.emit(this.knowledgeArea.id);
    }

    openNewKnowledgeArea() {
        this.onOpenNewKnowledgeArea.emit(this.knowledgeArea.id);
    }

    close() {
        this.onClose.emit();
    }

    edit() {
        this.isEditing = true;
    }

    cancel() {
        this.form.reset();
        this.isEditing = false;

        // canceling when creating a new knowledge area closes it
        if (this.knowledgeArea.id === undefined) {
            this.onClose.emit();
        }
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.form.controls.description.setValue(content);
        this.form.controls.description.markAsDirty();
    }

    /**
     * Creates a validator that verifies that updating a knowledge area cannot lead to circular dependencies
     * (I.e. the new parent of a knowledge area must not be itself or one of its current descendants)
     */
    private createNoCircularDependencyValidator() {
        // if the knowledgeArea is new, no validator is needed.
        if (this.knowledgeArea.id === undefined) {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            return (parentIdControl: FormControl<number | undefined>) => null;
        }
        return (parentIdControl: FormControl<number | undefined>) => {
            if (parentIdControl.value === undefined) {
                return null;
            }
            if (this.selfOrDescendantsHaveId(this.knowledgeArea, parentIdControl.value)) {
                return {
                    circularDependency: true,
                };
            }
            return null;
        };
    }

    /**
     * Checks if the given knowledge or one of its descendants have the given id
     * @param knowledgeArea the knowledge area to check
     * @param id the id to check for
     * @private
     */
    private selfOrDescendantsHaveId(knowledgeArea: KnowledgeAreaDTO, id: number) {
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
