import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';

@Component({
    template: '',
})
export abstract class EditCourseCompetencyComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected lectureService = inject(LectureService);
    protected router = inject(Router);
    protected alertService = inject(AlertService);

    isLoading = false;
    courseId: number;

    ngOnInit(): void {
        const paramMap = this.activatedRoute.parent!.parent!.snapshot.paramMap;
        this.courseId = Number(paramMap.get('courseId'));
    }
}
