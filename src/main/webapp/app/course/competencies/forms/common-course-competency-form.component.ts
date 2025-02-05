import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyTaxonomy, CourseCompetency, CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { merge } from 'rxjs';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { DateTimePickerType } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisFormsModule } from 'app/forms/artemis-forms.module';

@Component({
    selector: 'jhi-common-course-competency-form',
    templateUrl: './common-course-competency-form.component.html',
    styleUrls: ['./common-course-competency-form.component.scss'],
    imports: [
        ArtemisSharedModule,
        FormDateTimePickerModule,
        NgbDropdownModule,
        ArtemisFormsModule,
        ReactiveFormsModule,
        ArtemisCompetenciesModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownEditorModule,
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
