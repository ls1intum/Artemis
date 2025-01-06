import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CommonCourseCompetencyFormComponent } from 'app/course/competencies/forms/common-course-competency-form.component';
import { Competency } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-form',
    templateUrl: './competency-form.component.html',
    styleUrls: ['./competency-form.component.scss'],
    imports: [ArtemisSharedModule, CommonCourseCompetencyFormComponent],
})
export class CompetencyFormComponent extends CourseCompetencyFormComponent implements OnInit, OnChanges {
    @Input() formData: CourseCompetencyFormData = {
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
    };
    @Input() competency: Competency;

    @Output() formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
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
        this.formSubmitted.emit(competencyFormData);
    }
}
