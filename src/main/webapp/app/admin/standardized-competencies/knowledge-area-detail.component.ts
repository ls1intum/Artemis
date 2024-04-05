import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faBan, faPencil, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, KnowledgeAreaDTO, KnowledgeAreaValidators } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-knowledge-area-detail',
    templateUrl: './knowledge-area-detail.component.html',
})
export class KnowledgeAreaDetailComponent {
    //values for the knowledge area select
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input({ required: true }) set knowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        this._knowledgeArea = knowledgeArea;
        this.form = this.formBuilder.nonNullable.group({
            title: [knowledgeArea.title, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.TITLE_MAX)]],
            shortTile: [knowledgeArea.shortTitle, [Validators.required, Validators.maxLength(KnowledgeAreaValidators.SHORT_TITLE_MAX)]],
            description: [knowledgeArea.description, [Validators.maxLength(KnowledgeAreaValidators.DESCRIPTION_MAX)]],
            parentId: [knowledgeArea.parentId],
        });
        if (!this.isEditing) {
            this.form.disable();
        }
    }
    //TODO: add a custom validator that checks parentId against self or children!
    //TODO: another name for short title

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
    @Output() onDelete = new EventEmitter<void>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isEditingChange = new EventEmitter<boolean>();

    private _isEditing: boolean;
    private _knowledgeArea: KnowledgeAreaDTO;
    form: FormGroup<{
        title: FormControl<string | undefined>;
        shortTile: FormControl<string | undefined>;
        description: FormControl<string | undefined>;
        parentId: FormControl<number | undefined>;
    }>;

    //icons
    readonly faPencil = faPencil;
    readonly faTrash = faTrash;
    readonly faBan = faBan;
    readonly faSave = faSave;
    //other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = KnowledgeAreaValidators;

    constructor(private formBuilder: FormBuilder) {}

    save() {
        const updatedValues = this.form.getRawValue();
        const updatedKnowledgeArea: KnowledgeAreaDTO = { ...this.knowledgeArea, ...updatedValues };
        this.isEditing = false;
        this.onSave.emit(updatedKnowledgeArea);
    }

    delete() {
        this.onDelete.emit();
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

        //canceling when creating a new knowledge area closes it
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
}
