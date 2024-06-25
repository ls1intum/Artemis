import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { merge, of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TranslateService } from '@ngx-translate/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { intersection } from 'lodash-es';
import { CompetencyTaxonomy, CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

/**
 * Async Validator to make sure that a competency title is unique within a course
 */
export const titleUniqueValidator = (competencyService: CompetencyService, courseId: number, initialTitle?: string) => {
    return (competencyTitleControl: FormControl<string | undefined>) => {
        return of(competencyTitleControl.value).pipe(
            delay(250),
            switchMap((title) => {
                if (initialTitle && title === initialTitle) {
                    return of(null);
                }
                return competencyService.getCourseCompetencyTitles(courseId).pipe(
                    map((res) => {
                        const competencyTitles = res.body!;
                        if (title && competencyTitles.includes(title)) {
                            return {
                                titleUnique: { valid: false },
                            };
                        } else {
                            return null;
                        }
                    }),
                    catchError(() => of(null)),
                );
            }),
        );
    };
};

export interface CompetencyFormData {
    id?: number;
    title?: string;
    description?: string;
    softDueDate?: dayjs.Dayjs;
    taxonomy?: CompetencyTaxonomy;
    optional?: boolean;
    masteryThreshold?: number;
    connectedLectureUnits?: LectureUnit[];
}

@Component({
    selector: 'jhi-competency-form',
    templateUrl: './competency-form.component.html',
    styleUrls: ['./competency-form.component.scss'],
})
export class CompetencyFormComponent implements OnInit, OnChanges {
    @Input()
    formData: CompetencyFormData = {
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
        connectedLectureUnits: undefined,
    };

    @Input()
    isEditMode = false;
    @Input()
    isInConnectMode = false;
    @Input()
    isInSingleLectureMode = false;
    @Input()
    courseId: number;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input()
    averageStudentScore?: number;
    @Input()
    hasCancelButton: boolean;
    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();

    protected readonly competencyTaxonomy = CompetencyTaxonomy;
    protected readonly competencyValidators = CourseCompetencyValidators;

    @Output()
    formSubmitted: EventEmitter<CompetencyFormData> = new EventEmitter<CompetencyFormData>();

    form: FormGroup;
    selectedLectureInDropdown: Lecture;
    selectedLectureUnitsInTable: LectureUnit[] = [];
    suggestedTaxonomies: string[] = [];

    // Icons
    faTimes = faTimes;
    faQuestionCircle = faQuestionCircle;
    // Constants
    protected readonly DEFAULT_MASTERY_THRESHOLD = DEFAULT_MASTERY_THRESHOLD;

    constructor(
        private fb: FormBuilder,
        private competencyService: CompetencyService,
        private translateService: TranslateService,
        public lectureUnitService: LectureUnitService,
    ) {}

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get softDueDateControl() {
        return this.form.get('softDueDate');
    }

    get masteryThresholdControl() {
        return this.form.get('masteryThreshold');
    }

    get optionalControl() {
        return this.form.get('optional');
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

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        let initialTitle: string | undefined = undefined;
        if (this.isEditMode && this.formData && this.formData.title) {
            initialTitle = this.formData.title;
        }
        this.form = this.fb.nonNullable.group({
            title: [undefined as string | undefined, [Validators.required, Validators.maxLength(255)], [titleUniqueValidator(this.competencyService, this.courseId, initialTitle)]],
            description: [undefined as string | undefined, [Validators.maxLength(10000)]],
            softDueDate: [undefined],
            taxonomy: [undefined as CompetencyTaxonomy | undefined],
            masteryThreshold: [undefined, [Validators.min(0), Validators.max(100)]],
            optional: [false],
        });
        this.selectedLectureUnitsInTable = [];

        merge(this.titleControl!.valueChanges, this.descriptionControl!.valueChanges).subscribe(() => this.suggestTaxonomies());

        if (this.isInSingleLectureMode) {
            this.selectLectureInDropdown(this.lecturesOfCourseWithLectureUnits.first()!);
        }
    }

    private setFormValues(formData: CompetencyFormData) {
        this.form.patchValue(formData);
        if (formData.connectedLectureUnits) {
            this.selectedLectureUnitsInTable = formData.connectedLectureUnits;
        }
    }

    cancelForm() {
        this.onCancel.emit();
    }

    submitForm() {
        const competencyFormData: CompetencyFormData = { ...this.form.value };
        competencyFormData.connectedLectureUnits = this.selectedLectureUnitsInTable;
        this.formSubmitted.emit(competencyFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    selectLectureInDropdown(lecture: Lecture) {
        this.selectedLectureInDropdown = lecture;
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
            const keywords = this.translateService.instant('artemisApp.competency.keywords.' + taxonomy).split(', ');
            const taxonomyName = this.translateService.instant('artemisApp.competency.taxonomies.' + taxonomy);
            keywords.push(taxonomyName);
            if (keywords.map((keyword: string) => keyword.toLowerCase()).some((keyword: string) => title.includes(keyword) || description.includes(keyword))) {
                this.suggestedTaxonomies.push(taxonomyName);
            }
        }
    }

    selectLectureUnitInTable(lectureUnit: LectureUnit) {
        if (this.isLectureUnitAlreadySelectedInTable(lectureUnit)) {
            this.selectedLectureUnitsInTable.forEach((selectedLectureUnit, index) => {
                if (selectedLectureUnit.id === lectureUnit.id) {
                    this.selectedLectureUnitsInTable.splice(index, 1);
                }
            });
        } else {
            this.selectedLectureUnitsInTable.push(lectureUnit);
        }
    }

    isLectureUnitAlreadySelectedInTable(lectureUnit: LectureUnit) {
        return this.selectedLectureUnitsInTable.map((selectedLectureUnit) => selectedLectureUnit.id).includes(lectureUnit.id);
    }

    getLectureTitleForDropdown(lecture: Lecture) {
        const noOfSelectedUnitsInLecture = intersection(
            this.selectedLectureUnitsInTable.map((unit) => unit.id),
            lecture.lectureUnits?.map((unit) => unit.id),
        ).length;
        return this.translateService.instant('artemisApp.competency.createCompetency.dropdown', {
            lectureTitle: lecture.title,
            noOfConnectedUnits: noOfSelectedUnitsInLecture,
        });
    }
}
