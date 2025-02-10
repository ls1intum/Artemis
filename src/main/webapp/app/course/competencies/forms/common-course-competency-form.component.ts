import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyTaxonomy, CourseCompetency, CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { merge } from 'rxjs';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
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
export class CommonCourseCompetencyFormComponent implements OnInit, OnChanges {
    private translateService = inject(TranslateService);

    @Input() formData: CourseCompetencyFormData;
    @Input() isEditMode = false;
    @Input() isInConnectMode = false;
    @Input() isInSingleLectureMode = false;
    @Input() lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input() averageStudentScore?: number;
    @Input() form: FormGroup;
    @Input() courseCompetency: CourseCompetency;

    @Output() onTitleOrDescriptionChange = new EventEmitter<void>();

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
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get masteryThresholdControl() {
        return this.form.get('masteryThreshold');
    }

    get taxonomyControl() {
        return this.form.get('taxonomy') as FormControl;
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl?.setValue(content);
        this.descriptionControl?.markAsDirty();
    }

    ngOnInit() {
        merge(this.titleControl!.valueChanges, this.descriptionControl!.valueChanges).subscribe(() => this.suggestTaxonomies());
    }

    ngOnChanges() {
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
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
