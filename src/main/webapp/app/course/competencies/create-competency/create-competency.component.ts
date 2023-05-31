import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/entities/competency.model';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-create-competency',
    templateUrl: './create-competency.component.html',
    styles: [],
})
export class CreateCompetencyComponent implements OnInit {
    documentationType = DocumentationType.Competencies;
    competencyToCreate: Competency = new Competency();
    isLoading: boolean;
    courseId: number;
    lecturesWithLectureUnits: Lecture[] = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private lectureService: LectureService,
    ) {}

    ngOnInit(): void {
        this.competencyToCreate = new Competency();
        this.isLoading = true;
        this.activatedRoute
            .parent!.parent!.paramMap.pipe(
                take(1),
                switchMap((params) => {
                    this.courseId = Number(params.get('courseId'));
                    return this.lectureService.findAllByCourseId(this.courseId, true);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (lectureResult) => {
                    if (lectureResult.body) {
                        this.lecturesWithLectureUnits = lectureResult.body;
                        for (const lecture of this.lecturesWithLectureUnits) {
                            // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                            if (!lecture.lectureUnits) {
                                lecture.lectureUnits = [];
                            }
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    createCompetency(formData: CompetencyFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, masteryThreshold, connectedLectureUnits } = formData;

        this.competencyToCreate.title = title;
        this.competencyToCreate.description = description;
        this.competencyToCreate.taxonomy = taxonomy;
        this.competencyToCreate.masteryThreshold = masteryThreshold;
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
