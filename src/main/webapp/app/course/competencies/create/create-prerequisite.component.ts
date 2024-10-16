import { Component, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PrerequisiteFormComponent } from 'app/course/competencies/forms/prerequisite/prerequisite-form.component';
import { CreateCourseCompetencyComponent } from 'app/course/competencies/create/create-course-competency.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';

@Component({
    selector: 'jhi-create-prerequisite',
    templateUrl: './create-prerequisite.component.html',
    standalone: true,
    imports: [ArtemisSharedModule, PrerequisiteFormComponent, ArtemisSharedComponentModule],
})
export class CreatePrerequisiteComponent extends CreateCourseCompetencyComponent {
    private prerequisiteService = inject(PrerequisiteService);

    prerequisiteToCreate: Prerequisite = new Prerequisite();

    createPrerequisite(formData: CourseCompetencyFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, softDueDate, taxonomy, masteryThreshold, optional, connectedLectureUnits } = formData;

        this.prerequisiteToCreate.title = title;
        this.prerequisiteToCreate.description = description;
        this.prerequisiteToCreate.softDueDate = softDueDate;
        this.prerequisiteToCreate.taxonomy = taxonomy;
        this.prerequisiteToCreate.masteryThreshold = masteryThreshold;
        this.prerequisiteToCreate.optional = optional;
        this.prerequisiteToCreate.lectureUnits = connectedLectureUnits;

        this.isLoading = true;

        this.prerequisiteService
            .create(this.prerequisiteToCreate!, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    // currently at /course-management/{courseId}/prerequisite-management/create, going to /course-management/{courseId}/competency-management/, since prerequisite-management redirects to competency-management
                    this.router.navigate(['../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
