import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest, finalize, switchMap, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { faPlus, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';

@Component({
    selector: 'jhi-tutorial-groups-checklist',
    templateUrl: './tutorial-groups-checklist.component.html',
})
export class TutorialGroupsChecklistComponent implements OnInit {
    isLoading = false;
    course: Course;
    isTimeZoneConfigured = false;
    isTutorialGroupConfigurationCreated = false;

    faWrench = faWrench;
    faPlus = faPlus;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
    ) {}

    get isFullyConfigured(): boolean {
        return this.isTimeZoneConfigured && this.isTutorialGroupConfigurationCreated;
    }

    onNoClick(event: Event): void {
        event.preventDefault();
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return combineLatest([this.courseManagementService.find(courseId), this.tutorialGroupsConfigurationService.getOneOfCourse(courseId)]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([courseResult, configurationResult]) => {
                    if (courseResult.body) {
                        this.course = courseResult.body;
                        this.isTimeZoneConfigured = !!this.course.timeZone;
                    }
                    if (configurationResult.body) {
                        this.course.tutorialGroupsConfiguration = configurationResult.body;
                        this.isTutorialGroupConfigurationCreated = !!this.course.tutorialGroupsConfiguration;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
