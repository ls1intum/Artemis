import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-prerequisite-form',
    templateUrl: './prerequisite-form.component.html',
    styleUrls: ['./prerequisite-form.component.scss'],
    imports: [CommonCourseCompetencyFormComponent, FormsModule, ReactiveFormsModule, FontAwesomeModule, TranslateDirective],
})
export class PrerequisiteFormComponent extends CourseCompetencyFormComponent implements OnInit, OnChanges {
    @Input() formData: CourseCompetencyFormData = {
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
    };
    @Input() prerequisite: Prerequisite;

    @Output() formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();

    readonly CourseCompetencyType = CourseCompetencyType;

    ngOnChanges() {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit() {
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
