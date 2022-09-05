import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreeDayService } from 'app/course/tutorial-groups/tutorial-group-free-day.service';
import { TutorialGroupFreeDayFormData } from 'app/course/tutorial-groups/tutorial-free-days/crud/tutorial-free-day-form/tutorial-free-day-form.component';
import { finalize, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-create-tutorial-group-free-day',
    templateUrl: './create-tutorial-group-free-day.component.html',
    styleUrls: ['./create-tutorial-group-free-day.component.scss'],
})
export class CreateTutorialGroupFreeDayComponent implements OnInit {
    tutorialFreeDayToCreate: TutorialGroupFreeDay = new TutorialGroupFreeDay();
    isLoading: boolean;
    tutorialGroup: TutorialGroup;
    tutorialGroupConfigurationId: number;
    courseId: number;

    constructor(private tutorialFreeDayService: TutorialGroupFreeDayService, private router: Router, private activatedRoute: ActivatedRoute, private alertService: AlertService) {}

    ngOnInit(): void {
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(take(1))
            .subscribe({
                next: ([params, parentParams]) => {
                    this.tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.courseId = Number(parentParams.get('courseId'));
                },
            });
    }
    createTutorialGroupFreeDay(formData: TutorialGroupFreeDayFormData) {
        const { date, reason } = formData;

        this.tutorialFreeDayToCreate.date = dayjs(date);
        this.tutorialFreeDayToCreate.reason = reason;

        this.isLoading = true;
        this.tutorialFreeDayService
            .create(this.tutorialGroupConfigurationId, this.tutorialFreeDayToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.courseId, 'tutorial-groups-management', this.tutorialGroupConfigurationId, 'tutorial-free-days']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
