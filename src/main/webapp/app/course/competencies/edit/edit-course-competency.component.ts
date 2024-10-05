import { Component, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({ template: '' })
export abstract class EditCourseCompetencyComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected lectureService = inject(LectureService);
    protected router = inject(Router);
    protected alertService = inject(AlertService);

    isLoading = false;
    lecturesWithLectureUnits: Lecture[] = [];
    courseId: number;

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.parent?.parent?.paramMap
            .pipe(
                take(1),
                switchMap((parentParams) => {
                    this.courseId = Number(parentParams.get('courseId'));

                    return this.lectureService.findAllByCourseId(this.courseId, true);
                }),
            )
            .subscribe({
                next: (lecturesResult) => {
                    if (lecturesResult.body) {
                        this.lecturesWithLectureUnits = lecturesResult.body;
                        for (const lecture of this.lecturesWithLectureUnits) {
                            // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                            if (!lecture.lectureUnits) {
                                lecture.lectureUnits = [];
                            } else {
                                lecture.lectureUnits = lecture.lectureUnits.filter((lectureUnit) => lectureUnit.type !== LectureUnitType.EXERCISE);
                            }
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        // this.isLoading = false; is done in subclasses
    }
}
