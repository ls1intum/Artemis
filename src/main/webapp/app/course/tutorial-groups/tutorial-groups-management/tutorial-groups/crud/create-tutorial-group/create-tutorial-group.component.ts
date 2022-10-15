import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { finalize, map, switchMap } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-create-tutorial-group',
    templateUrl: './create-tutorial-group.component.html',
})
export class CreateTutorialGroupComponent implements OnInit {
    tutorialGroupToCreate: TutorialGroup = new TutorialGroup();
    isLoading: boolean;
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
        this.activatedRoute
            .parent!.paramMap.pipe(
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return this.courseManagementService.find(courseId).pipe(
                        finalize(() => {
                            this.isLoading = false;
                        }),
                    );
                }),
                map((res: HttpResponse<Course>) => res.body),
            )
            .subscribe({
                next: (course: Course) => {
                    this.course = course;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });

        this.tutorialGroupToCreate = new TutorialGroup();
    }

    createTutorialGroup(formData: TutorialGroupFormData) {
        // required fields
        if (!formData?.title || !formData?.teachingAssistant) {
            return;
        }

        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus, schedule } = formData;

        this.tutorialGroupToCreate.title = title;
        this.tutorialGroupToCreate.teachingAssistant = teachingAssistant;
        this.tutorialGroupToCreate.additionalInformation = additionalInformation;
        this.tutorialGroupToCreate.capacity = capacity;
        this.tutorialGroupToCreate.isOnline = isOnline;
        this.tutorialGroupToCreate.language = language;
        this.tutorialGroupToCreate.campus = campus;

        if (schedule) {
            this.tutorialGroupToCreate.tutorialGroupSchedule = new TutorialGroupSchedule();
            if (schedule.period && schedule.period.length === 2) {
                this.tutorialGroupToCreate.tutorialGroupSchedule.validFromInclusive = dayjs(schedule.period[0]);
                this.tutorialGroupToCreate.tutorialGroupSchedule.validToInclusive = dayjs(schedule.period[1]);
            }
            this.tutorialGroupToCreate.tutorialGroupSchedule.dayOfWeek = schedule.dayOfWeek;
            this.tutorialGroupToCreate.tutorialGroupSchedule.startTime = schedule.startTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.endTime = schedule.endTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.repetitionFrequency = schedule.repetitionFrequency;
            this.tutorialGroupToCreate.tutorialGroupSchedule.location = schedule.location;
        }

        this.isLoading = true;

        this.tutorialGroupService
            .create(this.tutorialGroupToCreate, this.course.id!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id, 'tutorial-groups-management']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
