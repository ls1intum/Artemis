import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { merge } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyTaxonomy, CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faBan, faQuestionCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { titleUniqueValidator } from '../competency-form/competency-form.component';

@Component({
    selector: 'jhi-prerequisite-form',
    templateUrl: './prerequisite-form.component.html',
    imports: [
        ArtemisCompetenciesModule,
        ArtemisSharedCommonModule,
        ArtemisSharedComponentModule,
        FormDateTimePickerModule,
        ArtemisMarkdownEditorModule,
        FontAwesomeModule,
        ReactiveFormsModule,
    ],
    standalone: true,
})
export class PrerequisiteFormComponent implements OnInit {
    @Input()
    prerequisite?: Prerequisite;
    @Input()
    courseId: number;

    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();
    @Output()
    onSubmit: EventEmitter<Prerequisite> = new EventEmitter<Prerequisite>();

    protected form: FormGroup<{
        title: FormControl<string | undefined>;
        description: FormControl<string | undefined>;
        taxonomy: FormControl<CompetencyTaxonomy | undefined>;
        softDueDate: FormControl<dayjs.Dayjs | undefined>;
        masteryThreshold: FormControl<number>;
        optional: FormControl<boolean>;
    }>;
    protected suggestedTaxonomies: string[] = [];

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    // Constants
    protected readonly competencyTaxonomy = CompetencyTaxonomy;
    protected readonly competencyValidators = CourseCompetencyValidators;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    // Services
    protected readonly formBuilder = inject(FormBuilder);
    protected readonly competencyService = inject(CompetencyService);
    protected readonly translateService = inject(TranslateService);

    ngOnInit(): void {
        this.form = this.formBuilder.nonNullable.group({
            title: [
                this.prerequisite?.title,
                [Validators.required, Validators.maxLength(CourseCompetencyValidators.TITLE_MAX)],
                [titleUniqueValidator(this.competencyService, this.courseId, this.prerequisite?.title)],
            ],
            description: [this.prerequisite?.description, [Validators.maxLength(CourseCompetencyValidators.DESCRIPTION_MAX)]],
            taxonomy: [this.prerequisite?.taxonomy],
            softDueDate: [this.prerequisite?.softDueDate],
            masteryThreshold: [
                this.prerequisite?.masteryThreshold ?? DEFAULT_MASTERY_THRESHOLD,
                [Validators.min(CourseCompetencyValidators.MASTERY_THRESHOLD_MIN), Validators.max(CourseCompetencyValidators.MASTERY_THRESHOLD_MAX)],
            ],
            optional: [this.prerequisite?.optional ?? false],
        });

        merge(this.form.controls.title.valueChanges, this.form.controls.description.valueChanges).subscribe(() => this.suggestTaxonomies());
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.form.controls.description.setValue(content);
        this.form.controls.description.markAsDirty();
    }

    cancel() {
        this.onCancel.emit();
    }

    submit() {
        const updatedValues = this.form.getRawValue();
        const updatedPrerequisite: Prerequisite = { ...this.prerequisite, ...updatedValues };

        this.onSubmit.emit(updatedPrerequisite);
    }

    /**
     * Suggest some taxonomies based on keywords used in the title or description.
     * Triggered after the user changes the title or description input field.
     */
    suggestTaxonomies() {
        this.suggestedTaxonomies = [];
        const title = this.form.controls.title?.value?.toLowerCase() ?? '';
        const description = this.form.controls.description?.value?.toLowerCase() ?? '';
        for (const taxonomy in this.competencyTaxonomy) {
            const keywords = this.translateService.instant('artemisApp.competency.keywords.' + taxonomy).split(', ');
            const taxonomyName = this.translateService.instant('artemisApp.competency.taxonomies.' + taxonomy);
            keywords.push(taxonomyName);
            if (keywords.map((keyword: string) => keyword.toLowerCase()).some((keyword: string) => title.includes(keyword) || description.includes(keyword))) {
                this.suggestedTaxonomies.push(taxonomyName);
            }
        }
    }
}
