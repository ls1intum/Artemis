import { ChangeDetectionStrategy, Component, effect, inject, input, model, output } from '@angular/core';
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

/**
 * Form structure for standardized competency editing.
 */
interface StandardizedCompetencyForm {
    title: FormControl<string | undefined>;
    description: FormControl<string | undefined>;
    taxonomy: FormControl<CompetencyTaxonomy | undefined>;
    knowledgeAreaId: FormControl<number | undefined>;
    sourceId: FormControl<number | undefined>;
}

/**
 * Component for editing standardized competencies.
 * Provides a form for creating and updating competency details.
 */
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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StandardizedCompetencyEditComponent {
    private readonly formBuilder = inject(FormBuilder);

    /** Available knowledge areas for selection */
    readonly knowledgeAreas = input<KnowledgeArea[]>([]);

    /** Available sources for selection */
    readonly sources = input<Source[]>([]);

    /** The competency being edited (required) */
    readonly competency = input.required<StandardizedCompetencyDTO>();

    /** Whether the form is in editing mode (two-way binding) */
    readonly isEditing = model<boolean>(false);

    /** Observable for dialog error messages */
    readonly dialogError = input<Observable<string>>();

    /** Emitted when the competency is saved */
    readonly onSave = output<StandardizedCompetencyDTO>();

    /** Emitted when the competency should be deleted */
    readonly onDelete = output<number>();

    /** Emitted when the edit panel should be closed */
    readonly onClose = output<void>();

    /** The reactive form for editing competency properties */
    protected form: FormGroup<StandardizedCompetencyForm>;

    /** Icons */
    protected readonly faPencil = faPencil;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;

    /** Constants */
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly validators = StandardizedCompetencyValidators;

    constructor() {
        // Effect to initialize/update form when competency input changes
        effect(() => {
            const comp = this.competency();
            this.initializeForm(comp);
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
     * Initializes the form with the given competency data.
     * @param competency - The competency to populate the form with
     */
    private initializeForm(competency: StandardizedCompetencyDTO): void {
        this.form = this.formBuilder.nonNullable.group({
            title: [competency.title, [Validators.required, Validators.maxLength(StandardizedCompetencyValidators.TITLE_MAX)]],
            description: [competency.description, [Validators.maxLength(StandardizedCompetencyValidators.DESCRIPTION_MAX)]],
            taxonomy: [competency.taxonomy],
            knowledgeAreaId: [competency.knowledgeAreaId, [Validators.required]],
            sourceId: [competency.sourceId],
        });

        // Apply initial editing state
        if (!this.isEditing()) {
            this.form.disable();
        }
    }

    /**
     * Saves the competency with current form values.
     */
    save(): void {
        const updatedValues = this.form.getRawValue();
        const updatedCompetency: StandardizedCompetencyDTO = { ...this.competency(), ...updatedValues };
        this.isEditing.set(false);
        this.onSave.emit(updatedCompetency);
    }

    /**
     * Emits delete event for the current competency.
     */
    delete(): void {
        const id = this.competency().id;
        if (id !== undefined) {
            this.onDelete.emit(id);
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
     * If creating a new competency, closes the panel.
     */
    cancel(): void {
        this.form.reset();
        this.isEditing.set(false);

        // Canceling when creating a new competency closes it
        if (this.competency().id === undefined) {
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
}
