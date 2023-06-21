import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/entities/competency.model';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap, take } from 'rxjs/operators';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { combineLatest, forkJoin } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-edit-competency',
    templateUrl: './edit-competency.component.html',
    styles: [],
})
export class EditCompetencyComponent implements OnInit {
    isLoading = false;
    competency: Competency;
    lecturesWithLectureUnits: Lecture[] = [];
    formData: CompetencyFormData;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private lectureService: LectureService,
        private router: Router,
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const competencyId = Number(params.get('competencyId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const competencyObservable = this.competencyService.findById(competencyId, this.courseId);
                    const competencyCourseProgressObservable = this.competencyService.getCourseProgress(competencyId, this.courseId);
                    const lecturesObservable = this.lectureService.findAllByCourseId(this.courseId, true);
                    return forkJoin([competencyObservable, competencyCourseProgressObservable, lecturesObservable]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([competencyResult, courseProgressResult, lecturesResult]) => {
                    if (competencyResult.body) {
                        this.competency = competencyResult.body;
                        if (courseProgressResult.body) {
                            this.competency.courseProgress = courseProgressResult.body;
                        }
                        // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                        if (!this.competency.lectureUnits) {
                            this.competency.lectureUnits = [];
                        }
                    }
                    if (lecturesResult.body) {
                        this.lecturesWithLectureUnits = lecturesResult.body;
                        for (const lecture of this.lecturesWithLectureUnits) {
                            // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                            if (!lecture.lectureUnits) {
                                lecture.lectureUnits = [];
                            } else {
                                // Filter out exercise units, they should be added via the exercise management for now
                                // TODO: User experience improvements for linking learning objects when editing a competency
                                lecture.lectureUnits = lecture.lectureUnits.filter((lectureUnit) => lectureUnit.type !== LectureUnitType.EXERCISE);
                            }
                        }
                    }

                    this.formData = {
                        id: this.competency.id,
                        title: this.competency.title,
                        description: this.competency.description,
                        connectedLectureUnits: this.competency.lectureUnits,
                        taxonomy: this.competency.taxonomy,
                        masteryThreshold: this.competency.masteryThreshold,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateCompetency(formData: CompetencyFormData) {
        const { title, description, taxonomy, masteryThreshold, connectedLectureUnits } = formData;

        this.competency.title = title;
        this.competency.description = description;
        this.competency.taxonomy = taxonomy;
        this.competency.masteryThreshold = masteryThreshold;
        this.competency.lectureUnits = connectedLectureUnits;

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
