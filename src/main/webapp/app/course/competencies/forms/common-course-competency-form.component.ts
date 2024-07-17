import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TranslateService } from '@ngx-translate/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { intersection } from 'lodash-es';
import { CourseCompetencyValidators, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from 'app/forms/forms.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { merge } from 'rxjs';

@Component({
    selector: 'jhi-common-course-competency-form',
    templateUrl: './common-course-competency-form.component.html',
    styleUrls: ['./common-course-competency-form.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, FormDateTimePickerModule, ArtemisMarkdownModule, NgbDropdownModule, FormsModule, ArtemisCompetenciesModule, ArtemisSharedComponentModule],
})
export class CommonCourseCompetencyFormComponent implements OnInit, OnChanges {
    @Input()
    formData: CourseCompetencyFormData;
    @Input()
    isEditMode = false;
    @Input()
    isInConnectMode = false;
    @Input()
    isInSingleLectureMode = false;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input()
    averageStudentScore?: number;
    @Input()
    form: FormGroup;
    @Input()
    competencyType: 'competency' | 'prerequisite';

    @Output()
    onLectureUnitSelectionChange = new EventEmitter<LectureUnit[]>();
    @Output()
    onTitleOrDescriptionChange = new EventEmitter<void>();

    protected readonly competencyValidators = CourseCompetencyValidators;

    selectedLectureInDropdown: Lecture;
    selectedLectureUnitsInTable: LectureUnit[] = [];
    suggestedTaxonomies: string[] = [];

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faQuestionCircle = faQuestionCircle;
    // Constants
    protected readonly DEFAULT_MASTERY_THRESHOLD = DEFAULT_MASTERY_THRESHOLD;

    constructor(
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

    get optionalControl() {
        return this.form.get('optional');
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

    ngOnInit(): void {
        merge(this.titleControl!.valueChanges, this.descriptionControl!.valueChanges).subscribe(() => this.onTitleOrDescriptionChange.next());
    }

    ngOnChanges(): void {
        merge(this.titleControl!.valueChanges, this.descriptionControl!.valueChanges).subscribe(() => this.onTitleOrDescriptionChange.next());
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
        if (formData.connectedLectureUnits) {
            this.selectedLectureUnitsInTable = formData.connectedLectureUnits;
            this.onLectureUnitSelectionChange.next(this.selectedLectureUnitsInTable);
        }
    }

    selectLectureInDropdown(lecture: Lecture) {
        this.selectedLectureInDropdown = lecture;
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
        this.onLectureUnitSelectionChange.next(this.selectedLectureUnitsInTable);
    }

    isLectureUnitAlreadySelectedInTable(lectureUnit: LectureUnit) {
        return this.selectedLectureUnitsInTable.map((selectedLectureUnit) => selectedLectureUnit.id).includes(lectureUnit.id);
    }

    getLectureTitleForDropdown(lecture: Lecture) {
        const noOfSelectedUnitsInLecture = intersection(
            this.selectedLectureUnitsInTable.map((unit) => unit.id),
            lecture.lectureUnits?.map((unit) => unit.id),
        ).length;
        return this.translateService.instant('artemisApp.courseCompetency.create.dropdown', {
            lectureTitle: lecture.title,
            noOfConnectedUnits: noOfSelectedUnitsInLecture,
        });
    }
}
