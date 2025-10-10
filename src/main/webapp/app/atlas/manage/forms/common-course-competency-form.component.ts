import { Component, OnChanges, effect, inject, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyTaxonomy, CourseCompetency, CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/atlas/shared/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { Subscription, merge } from 'rxjs';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

@Component({
    selector: 'jhi-common-course-competency-form',
    templateUrl: './common-course-competency-form.component.html',
    styleUrls: ['./common-course-competency-form.component.scss'],
    imports: [
        NgbDropdownModule,
        ReactiveFormsModule,
        FormDateTimePickerComponent,
        TaxonomySelectComponent,
        MarkdownEditorMonacoComponent,
        HelpIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        MarkdownEditorMonacoComponent,
    ],
})
export class CommonCourseCompetencyFormComponent implements OnChanges {
    private translateService = inject(TranslateService);

    formData = input.required<CourseCompetencyFormData>();
    isEditMode = input<boolean>(false);
    isInConnectMode = input<boolean>(false);
    isInSingleLectureMode = input<boolean>(false);
    lecturesOfCourseWithLectureUnits = input<Lecture[]>([]);
    averageStudentScore = input<number>();
    form = input.required<FormGroup>();
    courseCompetency = input.required<CourseCompetency>();

    onTitleOrDescriptionChange = output<void>();

    protected readonly competencyValidators = CourseCompetencyValidators;
    protected readonly DateTimePickerType = DateTimePickerType;

    suggestedTaxonomies: string[] = [];

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faQuestionCircle = faQuestionCircle;
    // Constants
    protected readonly DEFAULT_MASTERY_THRESHOLD = DEFAULT_MASTERY_THRESHOLD;
    protected readonly competencyTaxonomy = CompetencyTaxonomy;

    get titleControl() {
        return this.form().get('title');
    }

    get descriptionControl() {
        return this.form().get('description');
    }

    get masteryThresholdControl() {
        return this.form().get('masteryThreshold');
    }

    get taxonomyControl() {
        return this.form().get('taxonomy') as FormControl;
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl?.setValue(content);
        this.descriptionControl?.markAsDirty();
        this.onTitleOrDescriptionChange.emit();
    }

    constructor() {
        effect((onCleanup) => {
            const titleCtrl = this.titleControl;
            const descCtrl = this.descriptionControl;
            if (titleCtrl && descCtrl) {
                const subscription: Subscription = merge(titleCtrl.valueChanges, descCtrl.valueChanges).subscribe(() => {
                    this.suggestTaxonomies();
                    this.onTitleOrDescriptionChange.emit();
                });
                onCleanup(() => subscription.unsubscribe());
            }
        });
    }

    ngOnChanges() {
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData());
        }
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form().patchValue(formData);
    }

    /**
     * Suggest some taxonomies based on keywords used in the title or description.
     * Triggered after the user changes the title or description input field.
     */
    suggestTaxonomies() {
        this.suggestedTaxonomies = [];
        const title = this.titleControl?.value?.toLowerCase() ?? '';
        const description = this.descriptionControl?.value?.toLowerCase() ?? '';
        for (const taxonomy in this.competencyTaxonomy) {
            const keywords = this.translateService.instant('artemisApp.courseCompetency.keywords.' + taxonomy).split(', ');
            const taxonomyName = this.translateService.instant('artemisApp.courseCompetency.taxonomies.' + taxonomy);
            keywords.push(taxonomyName);
            if (keywords.map((keyword: string) => keyword.toLowerCase()).some((keyword: string) => title.includes(keyword) || description.includes(keyword))) {
                this.suggestedTaxonomies.push(taxonomyName);
            }
        }
    }
}
