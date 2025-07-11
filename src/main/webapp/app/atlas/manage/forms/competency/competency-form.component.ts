import { Component, OnChanges, effect, input, output } from '@angular/core';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgIf } from '@angular/common';

@Component({
    selector: 'jhi-competency-form',
    templateUrl: './competency-form.component.html',
    styleUrls: ['./competency-form.component.scss'],
    imports: [CommonCourseCompetencyFormComponent, FormsModule, ReactiveFormsModule, FontAwesomeModule, TranslateDirective, NgIf],
})
export class CompetencyFormComponent extends CourseCompetencyFormComponent implements OnChanges {
    formData = input<CourseCompetencyFormData>({
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
    });
    competency = input.required<Competency>();
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<CourseCompetencyFormData>();

    constructor() {
        super();
        effect(() => {
            this.initializeForm();
        });
    }

    ngOnChanges() {
        this.initializeForm();
        if (this.isEditMode() && this.formData()) {
            this.setFormValues(this.formData());
        }
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const competencyFormData: CourseCompetencyFormData = { ...this.form.value };
        this.formSubmitted.emit(competencyFormData);
    }
}
