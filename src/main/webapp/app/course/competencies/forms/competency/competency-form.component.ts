import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CommonCourseCompetencyFormComponent } from 'app/course/competencies/forms/common-course-competency-form.component';
import { CourseCompetencyType } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-form',
    templateUrl: './competency-form.component.html',
    styleUrls: ['./competency-form.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, CommonCourseCompetencyFormComponent],
})
export class CompetencyFormComponent extends CourseCompetencyFormComponent implements OnInit, OnChanges {
    @Input()
    formData: CourseCompetencyFormData = {
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
        connectedLectureUnits: undefined,
    };

    @Output()
    formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();

    readonly CourseCompetencyType = CourseCompetencyType;

    constructor(fb: FormBuilder, lectureUnitService: LectureUnitService, competencyService: CompetencyService, translateService: TranslateService) {
        super(fb, lectureUnitService, competencyService, translateService);
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode() && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const competencyFormData: CourseCompetencyFormData = { ...this.form.value };
        competencyFormData.connectedLectureUnits = this.selectedLectureUnitsInTable;
        this.formSubmitted.emit(competencyFormData);
    }
}
