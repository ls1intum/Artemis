import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { faBan, faPencil, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, Source, StandardizedCompetencyDTO, StandardizedCompetencyValidators } from 'app/atlas/shared/entities/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { Observable } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-standardized-competency-edit',
    templateUrl: './standardized-competency-edit.component.html',
    imports: [
        TranslateDirective,
        ButtonComponent,
        DeleteButtonDirective,
        FaIconComponent,
        FormsModule,
        ReactiveFormsModule,
        MarkdownEditorMonacoComponent,
        TaxonomySelectComponent,
        HtmlForMarkdownPipe,
    ],
})
export class StandardizedCompetencyEditComponent {
    private formBuilder = inject(FormBuilder);

    // values for the knowledge area select
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    // values for the source select
    @Input() sources: Source[] = [];
    @Input({ required: true }) set competency(competency: StandardizedCompetencyDTO) {
        this._competency = competency;
        this.form = this.formBuilder.nonNullable.group({
            title: [competency.title, [Validators.required, Validators.maxLength(StandardizedCompetencyValidators.TITLE_MAX)]],
            description: [competency.description, [Validators.maxLength(StandardizedCompetencyValidators.DESCRIPTION_MAX)]],
            taxonomy: [competency.taxonomy],
            knowledgeAreaId: [competency.knowledgeAreaId, [Validators.required]],
            sourceId: [competency.sourceId],
        });
        if (!this.isEditing) {
            this.form.disable();
        }
    }

    get competency() {
        return this._competency;
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

    @Output() onSave = new EventEmitter<StandardizedCompetencyDTO>();
    @Output() onDelete = new EventEmitter<number>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isEditingChange = new EventEmitter<boolean>();

    private _isEditing: boolean;
    private _competency: StandardizedCompetencyDTO;
    protected form: FormGroup<{
        title: FormControl<string | undefined>;
        description: FormControl<string | undefined>;
        taxonomy: FormControl<CompetencyTaxonomy | undefined>;
        knowledgeAreaId: FormControl<number | undefined>;
        sourceId: FormControl<number | undefined>;
    }>;

    // icons
    protected readonly faPencil = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    // other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = StandardizedCompetencyValidators;

    save() {
        const updatedValues = this.form.getRawValue();
        const updatedCompetency: StandardizedCompetencyDTO = Object.assign({}, this.competency, updatedValues);
        this.isEditing = false;
        this.onSave.emit(updatedCompetency);
    }

    delete() {
        this.onDelete.emit(this.competency.id);
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

        // canceling when creating a new competency closes it
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
