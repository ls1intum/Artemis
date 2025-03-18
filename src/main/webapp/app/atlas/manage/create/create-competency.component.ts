import { Component, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/entities/competency.model';
import { CompetencyService } from 'app/atlas/manage/competency.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';

import { CreateCourseCompetencyComponent } from 'app/atlas/manage/create/create-course-competency.component';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-create-competency',
    templateUrl: './create-competency.component.html',
    imports: [CompetencyFormComponent, DocumentationButtonComponent, TranslateDirective],
})
export class CreateCompetencyComponent extends CreateCourseCompetencyComponent {
    private competencyService = inject(CompetencyService);

    competencyToCreate: Competency = new Competency();

    createCompetency(formData: CourseCompetencyFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, softDueDate, taxonomy, masteryThreshold, optional } = formData;

        this.competencyToCreate.title = title;
        this.competencyToCreate.description = description;
        this.competencyToCreate.softDueDate = softDueDate;
        this.competencyToCreate.taxonomy = taxonomy;
        this.competencyToCreate.masteryThreshold = masteryThreshold;
        this.competencyToCreate.optional = optional;

        this.isLoading = true;

        this.competencyService
            .create(this.competencyToCreate!, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    // currently at /course-management/{courseId}/competency-management/create, going back to /course-management/{courseId}/competency-management/
                    this.router.navigate(['../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
