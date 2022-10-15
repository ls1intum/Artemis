import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-edit-tutorial-group',
    templateUrl: './edit-tutorial-group.component.html',
})
export class EditTutorialGroupComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    formData: TutorialGroupFormData;
    tutorialGroupId: number;
    course: Course;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.courseManagementService.find(this.courseId);
                }),
                map((res: HttpResponse<Course>) => res.body),
                switchMap((course: Course) => {
                    this.course = course;
                    return this.tutorialGroupService.getOneOfCourse(this.courseId, this.tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroup = tutorialGroupResult.body;
                        this.formData = {
                            title: this.tutorialGroup.title,
                            teachingAssistant: this.tutorialGroup.teachingAssistant,
                            additionalInformation: this.tutorialGroup.additionalInformation,
                            capacity: this.tutorialGroup.capacity,
                            isOnline: this.tutorialGroup.isOnline,
                            language: this.tutorialGroup.language,
                            campus: this.tutorialGroup.campus,
                        };
                        if (this.tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = this.tutorialGroup.tutorialGroupSchedule;
                            this.formData.schedule = {
                                period: [this.tutorialGroupSchedule.validFromInclusive!.toDate(), this.tutorialGroupSchedule.validToInclusive!.toDate()],
                                repetitionFrequency: this.tutorialGroupSchedule.repetitionFrequency,
                                startTime: this.tutorialGroupSchedule.startTime,
                                endTime: this.tutorialGroupSchedule.endTime,
                                dayOfWeek: this.tutorialGroupSchedule.dayOfWeek,
                                location: this.tutorialGroupSchedule.location,
                            };
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroup(formData: TutorialGroupFormData) {
        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus, schedule } = formData;
        const updatedTutorialGroup = new TutorialGroup();
        updatedTutorialGroup.id = this.tutorialGroup.id;
        updatedTutorialGroup.title = title;
        updatedTutorialGroup.teachingAssistant = teachingAssistant;
        updatedTutorialGroup.additionalInformation = additionalInformation;
        updatedTutorialGroup.capacity = capacity;
        updatedTutorialGroup.isOnline = isOnline;
        updatedTutorialGroup.language = language;
        updatedTutorialGroup.campus = campus;
        if (schedule) {
            updatedTutorialGroup.tutorialGroupSchedule = new TutorialGroupSchedule();
            if (this.tutorialGroup.tutorialGroupSchedule) {
                updatedTutorialGroup.tutorialGroupSchedule.id = this.tutorialGroup.tutorialGroupSchedule.id;
            }
            const { endTime, startTime, dayOfWeek, repetitionFrequency, period, location } = schedule;
            if (period && period.length === 2) {
                updatedTutorialGroup.tutorialGroupSchedule.validFromInclusive = dayjs(period[0]);
                updatedTutorialGroup.tutorialGroupSchedule.validToInclusive = dayjs(period[1]);
            }
            updatedTutorialGroup.tutorialGroupSchedule.dayOfWeek = dayOfWeek;
            updatedTutorialGroup.tutorialGroupSchedule.startTime = startTime;
            updatedTutorialGroup.tutorialGroupSchedule.endTime = endTime;
            updatedTutorialGroup.tutorialGroupSchedule.repetitionFrequency = repetitionFrequency;
            updatedTutorialGroup.tutorialGroupSchedule.location = location;
        } else {
            updatedTutorialGroup.tutorialGroupSchedule = undefined;
        }

        this.isLoading = true;
        this.tutorialGroupService
            .update(this.courseId, this.tutorialGroupId, updatedTutorialGroup)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.router.navigate(['/course-management', this.courseId, 'tutorial-groups-management']);
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => this.onError(res),
            });
    }

    onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error && error.errorKey && error.errorKey === 'scheduleOverlapsWithSession') {
            this.alertService.error(error.message, error.params);
        } else {
            this.alertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
        }
    }
}
