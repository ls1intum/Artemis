import { Component, effect, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';

@Component({
    template: '',
})
export abstract class CreateCourseCompetencyComponent {
    protected activatedRoute = inject(ActivatedRoute);
    protected router = inject(Router);
    protected alertService = inject(AlertService);
    protected lectureService = inject(LectureService);

    readonly documentationType: DocumentationType = 'Competencies';

    isLoading: boolean;
    courseId: number;
    lecturesWithLectureUnits: Lecture[] = [];

    constructor() {
        effect(() => {
            this.initialize();
        });
    }

    private initialize(): void {
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
}
