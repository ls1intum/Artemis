import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faBan, faPencil, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, StandardizedCompetencyDTO, StandardizedCompetencyValidators } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
})
export class StandardizedCompetencyDetailComponent {
    //values for the knowledge area select
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input({ required: true }) set competency(competency: StandardizedCompetencyDTO) {
        this._competency = competency;
        this.form = this.formBuilder.nonNullable.group({
            title: [competency.title, [Validators.required, Validators.maxLength(StandardizedCompetencyValidators.TITLE_MAX)]],
            description: [competency.description, [Validators.maxLength(StandardizedCompetencyValidators.DESCRIPTION_MAX)]],
            taxonomy: [competency.taxonomy],
            knowledgeAreaId: [competency.knowledgeAreaId, [Validators.required]],
        });
        if (!this.isInEditMode) {
            this.form.disable();
        }
    }

    get competency() {
        return this._competency;
    }

    @Input() set isInEditMode(isInEditMode: boolean) {
        this._isInEditMode = isInEditMode;
        this.isInEditModeChange.emit(isInEditMode);
        if (isInEditMode) {
            this.form.enable();
        } else {
            this.form.disable();
        }
    }

    get isInEditMode() {
        return this._isInEditMode;
    }

    @Input() dialogError: Observable<string>;

    @Output() onSave = new EventEmitter<StandardizedCompetencyDTO>();
    @Output() onDelete = new EventEmitter<void>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isInEditModeChange = new EventEmitter<boolean>();

    private _isInEditMode: boolean;
    private _competency: StandardizedCompetencyDTO;
    form: FormGroup<{
        title: FormControl<string | undefined>;
        description: FormControl<string | undefined>;
        taxonomy: FormControl<CompetencyTaxonomy | undefined>;
        knowledgeAreaId: FormControl<number | undefined>;
    }>;

    //icons
    readonly faPencil = faPencil;
    readonly faTrash = faTrash;
    readonly faBan = faBan;
    readonly faSave = faSave;
    //other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = StandardizedCompetencyValidators;

    constructor(private formBuilder: FormBuilder) {}

    save() {
        const updatedValues = this.form.getRawValue();
        const updatedCompetency: StandardizedCompetencyDTO = { ...this.competency, ...updatedValues };
        this.isInEditMode = false;
        this.onSave.emit(updatedCompetency);
    }

    delete() {
        this.onDelete.emit();
    }

    close() {
        this.onClose.emit();
    }

    edit() {
        this.isInEditMode = true;
    }

    cancel() {
        this.form.reset();
        this.isInEditMode = false;

        //canceling when creating a new competency closes it
        if (this.competency.id === undefined) {
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
