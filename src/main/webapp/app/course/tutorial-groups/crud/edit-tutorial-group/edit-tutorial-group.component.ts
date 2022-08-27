import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupSchedule } from 'app/entities/tutorialGroupSchedule.model';
import timezones from 'timezones-list';

@Component({
    selector: 'jhi-edit-tutorial-group',
    templateUrl: './edit-tutorial-group.component.html',
})
export class EditTutorialGroupComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    formData: TutorialGroupFormData;
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.getOneOfCourse(tutorialGroupId, this.courseId);
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
                                timeZone: timezones.find((tz) => tz.tzCode === this.tutorialGroupSchedule.timeZone)!,
                                validFromInclusive: this.tutorialGroupSchedule.validFromInclusive,
                                validToInclusive: this.tutorialGroupSchedule.validToInclusive,
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
        // required fields
        // ToDo: Check all other required fields and check form vor validity errors
        if (!formData?.title || !formData?.teachingAssistant) {
            return;
        }

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
            this.tutorialGroup.tutorialGroupSchedule.validFromInclusive = schedule.validFromInclusive ? schedule.validFromInclusive : undefined;
            this.tutorialGroup.tutorialGroupSchedule.validToInclusive = schedule.validToInclusive ? schedule.validToInclusive : undefined;
            this.tutorialGroup.tutorialGroupSchedule.dayOfWeek = schedule.dayOfWeek;
            this.tutorialGroup.tutorialGroupSchedule.startTime = schedule.startTime;
            this.tutorialGroup.tutorialGroupSchedule.endTime = schedule.endTime;
            this.tutorialGroup.tutorialGroupSchedule.repetitionFrequency = schedule.repetitionFrequency;
            this.tutorialGroup.tutorialGroupSchedule.timeZone = schedule.timeZone.tzCode;
        }

        this.isLoading = true;
        this.tutorialGroupService
            .update(this.tutorialGroup, this.courseId)
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
