import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
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

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    const courseId = Number(parentParams.get('courseId'));
                    return this.courseManagementService.find(courseId);
                }),
                map((res: HttpResponse<Course>) => res.body),
                switchMap((course: Course) => {
                    this.course = course;
                    return this.tutorialGroupService.getOneOfCourse(this.tutorialGroupId, this.course.id!);
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
                            location: this.tutorialGroup.location,
                        };
                        if (this.tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = this.tutorialGroup.tutorialGroupSchedule;
                            this.formData.schedule = {
                                period: [this.tutorialGroupSchedule.validFromInclusive!.toDate(), this.tutorialGroupSchedule.validToInclusive!.toDate()],
                                repetitionFrequency: this.tutorialGroupSchedule.repetitionFrequency,
                                startTime: this.tutorialGroupSchedule.startTime,
                                endTime: this.tutorialGroupSchedule.endTime,
                                dayOfWeek: this.tutorialGroupSchedule.dayOfWeek,
                            };
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroup(formData: TutorialGroupFormData) {
        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, location, schedule } = formData;
        this.tutorialGroup.title = title;
        this.tutorialGroup.teachingAssistant = teachingAssistant;
        this.tutorialGroup.additionalInformation = additionalInformation;
        this.tutorialGroup.capacity = capacity;
        this.tutorialGroup.isOnline = isOnline;
        this.tutorialGroup.language = language;
        this.tutorialGroup.location = location;
        if (schedule) {
            if (!this.tutorialGroup.tutorialGroupSchedule) {
                this.tutorialGroup.tutorialGroupSchedule = new TutorialGroupSchedule();
            }
            const { endTime, startTime, dayOfWeek, repetitionFrequency, period } = schedule;
            if (period && period.length === 2) {
                this.tutorialGroup.tutorialGroupSchedule.validFromInclusive = dayjs(period[0]);
                this.tutorialGroup.tutorialGroupSchedule.validToInclusive = dayjs(period[1]);
            }
            this.tutorialGroup.tutorialGroupSchedule.dayOfWeek = dayOfWeek;
            this.tutorialGroup.tutorialGroupSchedule.startTime = startTime;
            this.tutorialGroup.tutorialGroupSchedule.endTime = endTime;
            this.tutorialGroup.tutorialGroupSchedule.repetitionFrequency = repetitionFrequency;
        }

        this.isLoading = true;
        this.tutorialGroupService
            .update(this.tutorialGroup, this.course.id!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // navigate back to unit-management from :courseId/tutorial-groups-management/:tutorialGroupId/edit
                    this.router.navigate(['../..'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
