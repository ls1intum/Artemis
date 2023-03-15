import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-create-tutorial-group',
    templateUrl: './create-tutorial-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTutorialGroupComponent implements OnInit, OnDestroy {
    tutorialGroupToCreate: TutorialGroup = new TutorialGroup();
    isLoading: boolean;
    course: Course;

    ngUnsubscribe = new Subject<void>();
    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntil(this.ngUnsubscribe)).subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
        });
        this.tutorialGroupToCreate = new TutorialGroup();
    }

    createTutorialGroup(formData: TutorialGroupFormData) {
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
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
