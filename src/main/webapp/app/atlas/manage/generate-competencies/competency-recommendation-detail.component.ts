import { Component, computed, effect, input, model, output, signal } from '@angular/core';
import { CourseCompetencyValidators } from 'app/atlas/shared/entities/competency.model';
import { faChevronRight, faPen, faSave, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CompetencyFormControlsWithViewed } from 'app/atlas/manage/generate-competencies/generate-competencies.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-competency-recommendation',
    templateUrl: './competency-recommendation-detail.component.html',
    styleUrls: ['competency-recommendation-detail.component.scss'],
    imports: [
        FormsModule,
        ReactiveFormsModule,
        FaIconComponent,
        TranslateDirective,
        ButtonComponent,
        NgbCollapse,
        MarkdownEditorMonacoComponent,
        TaxonomySelectComponent,
        HtmlForMarkdownPipe,
    ],
})
export class CompetencyRecommendationDetailComponent {
    form = input<FormGroup<CompetencyFormControlsWithViewed>>();
    index = input<number>(0);
    isCollapsed = model<boolean>(true);
    isInEditMode = signal<boolean>(false);

    onDelete = output<void>();

    protected readonly faChevronRight = faChevronRight;
    protected readonly faTrash = faTrash;
    protected readonly faWrench = faWrench;
    protected readonly faSave = faSave;
    protected readonly faPen = faPen;

    //Other constants for html
    protected readonly competencyValidators = CourseCompetencyValidators;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    constructor() {
        effect(() => {
            const form = this.form();
            if (!form) {
                return;
            }
            this.titleControl()?.addValidators([Validators.required, Validators.maxLength(CourseCompetencyValidators.TITLE_MAX)]);
            this.descriptionControl()?.addValidators([Validators.maxLength(CourseCompetencyValidators.DESCRIPTION_MAX)]);
            form.controls.competency.disable();
            this.viewedControl()?.enable();
        });
    }

    /**
     * Toggles collapsed status and sets viewed to true
     */
    toggle() {
        this.isCollapsed.set(!this.isCollapsed());
        this.viewedControl()?.setValue(true);
    }

    /**
     * Sends event to parent to handle delete
     */
    delete() {
        this.onDelete.emit();
    }

    /**
     * Enters edit mode: Enables all form fields and expands the element
     */
    edit() {
        const form = this.form();
        if (!form) {
            return;
        }
        form.controls.competency.enable();
        this.isInEditMode.set(true);
        this.isCollapsed.set(false);
        this.viewedControl()?.setValue(true);
    }

    /**
     * Leaves edit mode: Disables all form fields again and collapses the element
     */
    save() {
        const form = this.form();
        if (!form) {
            return;
        }
        form.controls.competency.disable();
        this.isInEditMode.set(false);
        this.isCollapsed.set(true);
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl()?.setValue(content);
        this.descriptionControl()?.markAsDirty();
    }

    /**
     * Only allows save if no form controls have validation errors
     */
    isSavePossible = computed(() => {
        const form = this.form();
        return !!form && !form.invalid;
    });

    // control accessors as computed signals to avoid unsafe non-null assertions
    titleControl = computed(() => this.form()?.controls.competency.controls.title);
    descriptionControl = computed(() => this.form()?.controls.competency.controls.description);
    taxonomyControl = computed(() => this.form()?.controls.competency.controls.taxonomy);
    viewedControl = computed(() => this.form()?.controls.viewed);
}
