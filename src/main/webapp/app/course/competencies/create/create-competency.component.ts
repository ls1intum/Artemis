import { Component, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CompetencyFormComponent } from 'app/course/competencies/forms/competency/competency-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CreateCourseCompetencyComponent } from 'app/course/competencies/create/create-course-competency.component';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';

@Component({
    selector: 'jhi-create-competency',
    templateUrl: './create-competency.component.html',
    styles: [],
    standalone: true,
    imports: [ArtemisSharedModule, CompetencyFormComponent, ArtemisSharedComponentModule],
})
export class CreateCompetencyComponent extends CreateCourseCompetencyComponent {
    private competencyService = inject(CompetencyService);

    competencyToCreate: Competency = new Competency();

    createCompetency(formData: CourseCompetencyFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, softDueDate, taxonomy, masteryThreshold, optional, connectedLectureUnits } = formData;

        this.competencyToCreate.title = title;
        this.competencyToCreate.description = description;
        this.competencyToCreate.softDueDate = softDueDate;
        this.competencyToCreate.taxonomy = taxonomy;
        this.competencyToCreate.masteryThreshold = masteryThreshold;
        this.competencyToCreate.optional = optional;
        this.competencyToCreate.lectureUnits = connectedLectureUnits;

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
