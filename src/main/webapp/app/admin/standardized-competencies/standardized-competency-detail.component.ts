import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faBan, faEdit, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaWithLevel, StandardizedCompetency, StandardizedCompetencyValidators } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    styleUrls: ['./standardized-competency-detail.component.scss'],
})
export class StandardizedCompetencyDetailComponent {
    @Input({ required: true }) competency: StandardizedCompetency;
    @Input() isInEditMode = false;
    @Input() knowledgeAreas: KnowledgeAreaWithLevel[] = [];

    @Output() onSave = new EventEmitter<StandardizedCompetency>();
    @Output() onDelete = new EventEmitter<number>();
    @Output() isInEditModeChange = new EventEmitter<boolean>();

    //copy to reset values when canceling the editing
    copy: StandardizedCompetency;
    //icons
    readonly faEdit = faEdit;
    readonly faTrash = faTrash;
    readonly faBan = faBan;
    readonly faSave = faSave;
    //other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    //TODO: set type of this formgroup!
    form: FormGroup;

    constructor(private formBuilder: FormBuilder) {
        this.form = this.formBuilder.nonNullable.group({
            title: ['^^', [Validators.required, Validators.maxLength(StandardizedCompetencyValidators.TITLE_MAX)]],
            description: ['^^', [Validators.required, Validators.maxLength(StandardizedCompetencyValidators.DESCRIPTION_MAX)]],
            taxonomy: [CompetencyTaxonomy.ANALYZE],
            knowledgeArea: [undefined as undefined | KnowledgeAreaWithLevel, [Validators.required]],
        });
    }

    delete() {
        this.onDelete.emit(this.competency.id);
    }

    edit() {
        this.setIsInEditMode(true);
        this.copy = Object.assign({}, this.competency);
    }

    cancel() {
        this.competency = this.copy;
        this.setIsInEditMode(false);
    }

    save() {
        this.setIsInEditMode(false);
        this.onSave.emit(this.competency);
    }

    setIsInEditMode(isInEditMode: boolean) {
        this.isInEditMode = isInEditMode;
        this.isInEditModeChange.emit(isInEditMode);
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl.setValue(content);
        this.descriptionControl.markAsDirty();
    }

    get titleControl() {
        return this.form.controls?.title;
    }

    get descriptionControl() {
        return this.form.controls?.description;
    }

    get taxonomyControl(): FormControl {
        return this.form.controls?.taxonomy as FormControl<CompetencyTaxonomy>;
    }

    get knowledgeAreaControl() {
        return this.form.controls?.knowledgeArea;
    }
}
