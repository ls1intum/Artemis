import { Component, effect, input } from '@angular/core';
import { CourseCompetencyFormComponent, CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';

import { CommonCourseCompetencyFormComponent } from 'app/atlas/manage/forms/common-course-competency-form.component';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-competency-form',
    templateUrl: './competency-form.component.html',
    styleUrls: ['./competency-form.component.scss'],
    imports: [CommonCourseCompetencyFormComponent, FormsModule, ReactiveFormsModule, FontAwesomeModule, TranslateDirective],
})
export class CompetencyFormComponent extends CourseCompetencyFormComponent {
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

    constructor() {
        super();
        // Replaces ngOnChanges: builds the form and patches it from the signal inputs. The effect tracks
        // courseId(), formData() and isEditMode() (the latter via updateTitleUniqueValidator() and the guard
        // below), i.e. every input the former hook reacted to. The template renders the child only inside
        // `@if (form)`, so there is no before-child-init ordering hazard despite the effect running after ngOnInit.
        effect(() => {
            this.courseId();
            if (!this.form) {
                this.initializeForm();
            }
            const fd = this.formData();
            this.updateTitleUniqueValidator();
            if (this.isEditMode() && fd) {
                this.setFormValues(fd);
            }
        });
    }

    private setFormValues(formData: CourseCompetencyFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const competencyFormData: CourseCompetencyFormData = { ...this.form.value };
        this.formSubmitted.emit(competencyFormData);
    }
}
