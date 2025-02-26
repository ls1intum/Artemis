import { Component, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest, forkJoin } from 'rxjs';

import { EditCourseCompetencyComponent } from 'app/course/competencies/edit/edit-course-competency.component';
import { PrerequisiteFormComponent } from 'app/course/competencies/forms/prerequisite/prerequisite-form.component';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';

@Component({
    selector: 'jhi-edit-prerequisite',
    templateUrl: './edit-prerequisite.component.html',
    imports: [PrerequisiteFormComponent],
})
export class EditPrerequisiteComponent extends EditCourseCompetencyComponent implements OnInit {
    private prerequisiteService = inject(PrerequisiteService);

    prerequisite: Prerequisite;
    formData: CourseCompetencyFormData;

    ngOnInit(): void {
        super.ngOnInit();

        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const prerequisiteId = Number(params.get('prerequisiteId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const prerequisiteObservable = this.prerequisiteService.findById(prerequisiteId, this.courseId);
                    const prerequisiteCourseProgressObservable = this.prerequisiteService.getCourseProgress(prerequisiteId, this.courseId);
                    return forkJoin([prerequisiteObservable, prerequisiteCourseProgressObservable]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([prerequisiteResult, courseProgressResult]) => {
                    if (prerequisiteResult.body) {
                        this.prerequisite = prerequisiteResult.body;
                        if (courseProgressResult.body) {
                            this.prerequisite.courseProgress = courseProgressResult.body;
                        }
                    }

                    this.formData = {
                        id: this.prerequisite.id,
                        title: this.prerequisite.title,
                        description: this.prerequisite.description,
                        softDueDate: this.prerequisite.softDueDate,
                        taxonomy: this.prerequisite.taxonomy,
                        masteryThreshold: this.prerequisite.masteryThreshold,
                        optional: this.prerequisite.optional,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateCompetency(formData: CourseCompetencyFormData) {
        const { title, description, softDueDate, taxonomy, masteryThreshold, optional } = formData;

        this.prerequisite.title = title;
        this.prerequisite.description = description;
        this.prerequisite.softDueDate = softDueDate;
        this.prerequisite.taxonomy = taxonomy;
        this.prerequisite.masteryThreshold = masteryThreshold;
        this.prerequisite.optional = optional;

        this.isLoading = true;

        this.prerequisiteService
            .update(this.prerequisite, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // currently at /course-management/{courseId}/prerequisite-management/{competencyId}/edit, going to /course-management/{courseId}/competency-management/
                    this.router.navigate(['../../../competency-management/'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
