import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';

@Component({
    template: '',
})
export abstract class CreateCourseCompetencyComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected router = inject(Router);
    protected alertService = inject(AlertService);
    protected lectureService = inject(LectureService);

    readonly documentationType: DocumentationType = 'Competencies';

    isLoading: boolean;
    courseId: number;

    ngOnInit(): void {
        const paramMap = this.activatedRoute.parent!.parent!.snapshot.paramMap;
        this.courseId = Number(paramMap.get('courseId'));
    }
}
