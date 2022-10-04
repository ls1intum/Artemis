import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-tutorial-group-detail',
    templateUrl: './course-tutorial-group-detail.component.html',
    styleUrls: ['./course-tutorial-group-detail.component.scss'],
})
export class CourseTutorialGroupDetailComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    courseId: number;
    tutorialGroupId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(params.get('courseId'));
                    return this.tutorialGroupService.getOneOfCourse(this.courseId, this.tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    this.tutorialGroup = tutorialGroupResult.body!;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
