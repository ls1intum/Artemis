import { Component, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { finalize, switchMap, take } from 'rxjs/operators';
import { CompetencyService } from 'app/atlas/manage/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest, forkJoin } from 'rxjs';
import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';

import { EditCourseCompetencyComponent } from 'app/atlas/manage/edit/edit-course-competency.component';
import { CourseCompetencyFormData } from 'app/atlas/manage/forms/course-competency-form.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-edit-competency',
    templateUrl: './edit-competency.component.html',
    imports: [CompetencyFormComponent, TranslateDirective],
})
export class EditCompetencyComponent extends EditCourseCompetencyComponent implements OnInit {
    private competencyService = inject(CompetencyService);

    competency: Competency;
    formData: CourseCompetencyFormData;

    ngOnInit(): void {
        super.ngOnInit();

        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const competencyId = Number(params.get('competencyId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const competencyObservable = this.competencyService.findById(competencyId, this.courseId);
                    const competencyCourseProgressObservable = this.competencyService.getCourseProgress(competencyId, this.courseId);
                    return forkJoin([competencyObservable, competencyCourseProgressObservable]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([competencyResult, courseProgressResult]) => {
                    if (competencyResult.body) {
                        this.competency = competencyResult.body;
                        if (courseProgressResult.body) {
                            this.competency.courseProgress = courseProgressResult.body;
                        }
                    }

                    this.formData = {
                        id: this.competency.id,
                        title: this.competency.title,
                        description: this.competency.description,
                        softDueDate: this.competency.softDueDate,
                        taxonomy: this.competency.taxonomy,
                        masteryThreshold: this.competency.masteryThreshold,
                        optional: this.competency.optional,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateCompetency(formData: CourseCompetencyFormData) {
        const { title, description, softDueDate, taxonomy, masteryThreshold, optional } = formData;

        this.competency.title = title;
        this.competency.description = description;
        this.competency.softDueDate = softDueDate;
        this.competency.taxonomy = taxonomy;
        this.competency.masteryThreshold = masteryThreshold;
        this.competency.optional = optional;

        this.isLoading = true;

        this.competencyService
            .update(this.competency, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // currently at /course-management/{courseId}/competency-management/{competencyId}/edit, going back to /course-management/{courseId}/competency-management/
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
