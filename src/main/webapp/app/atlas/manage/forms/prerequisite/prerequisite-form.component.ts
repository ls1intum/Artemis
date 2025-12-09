import { Component, OnChanges, SimpleChanges, effect, input } from '@angular/core';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-prerequisite-form',
    templateUrl: './prerequisite-form.component.html',
    styleUrls: ['./prerequisite-form.component.scss'],
    imports: [CommonCourseCompetencyFormComponent, FormsModule, ReactiveFormsModule, FontAwesomeModule, TranslateDirective],
})
export class PrerequisiteFormComponent extends CourseCompetencyFormComponent implements OnChanges {
    formData = input<CourseCompetencyFormData>({
        id: undefined,
        title: undefined,
        description: undefined,
        softDueDate: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
    });
    prerequisite = input.required<Prerequisite>();

    readonly CourseCompetencyType = CourseCompetencyType;

    constructor() {
        super();
        effect(() => {
            this.courseId();
            if (!this.form) {
                this.initializeForm();
            }
            const fd = this.formData();
            if (this.isEditMode() && fd) {
                this.setFormValues(fd);
            }
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        this.initializeForm();
        const fd = this.formData();
        if (this.isEditMode() && fd) {
            this.setFormValues(fd);
        }
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const competencyFormData: CourseCompetencyFormData = Object.assign({}, this.form.value);
        this.formSubmitted.emit(competencyFormData);
    }
}
