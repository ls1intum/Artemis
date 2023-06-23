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
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

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
                return competencyService.getAllForCourse(courseId).pipe(
                    map((res) => {
                        let competencyTitles: string[] = [];
                        if (res.body) {
                            competencyTitles = res.body.map((competency) => competency.title!);
                        }
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
    taxonomy?: CompetencyTaxonomy;
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
        taxonomy: undefined,
        masteryThreshold: undefined,
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

    titleUniqueValidator = titleUniqueValidator;
    competencyTaxonomy = CompetencyTaxonomy;

    @Output()
    formSubmitted: EventEmitter<CompetencyFormData> = new EventEmitter<CompetencyFormData>();

    form: FormGroup;
    selectedLectureInDropdown: Lecture;
    selectedLectureUnitsInTable: LectureUnit[] = [];
    suggestedTaxonomies: string[] = [];

    faTimes = faTimes;

    constructor(private fb: FormBuilder, private competencyService: CompetencyService, private translateService: TranslateService, public lectureUnitService: LectureUnitService) {}

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get masteryThresholdControl() {
        return this.form.get('masteryThreshold');
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
        this.form = this.fb.group({
            title: [
                undefined as string | undefined,
                [Validators.required, Validators.maxLength(255)],
                [this.titleUniqueValidator(this.competencyService, this.courseId, initialTitle)],
            ],
            description: [undefined as string | undefined, [Validators.maxLength(10000)]],
            taxonomy: [undefined, [Validators.pattern('^(' + Object.keys(this.competencyTaxonomy).join('|') + ')$')]],
            masteryThreshold: [undefined, [Validators.min(0), Validators.max(100)]],
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
     * Needed to keep the order in keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };

    /**
     * Suggest some taxonomies based on keywords used in the title or description.
     * Triggered after the user changes the title or description input field.
     */
    suggestTaxonomies() {
        this.suggestedTaxonomies = [];
        const title = this.titleControl?.value?.toLowerCase() ?? '';
        const description = this.descriptionControl?.value?.toLowerCase() ?? '';
        for (const taxonomy in this.competencyTaxonomy) {
            const keywords = this.translateService.instant('artemisApp.competency.keywords.' + taxonomy.toLowerCase()).split(', ');
            const taxonomyName = this.translateService.instant('artemisApp.competency.taxonomies.' + taxonomy.toLowerCase());
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
