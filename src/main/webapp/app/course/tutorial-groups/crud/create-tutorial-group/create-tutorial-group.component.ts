import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupSchedule } from 'app/entities/tutorialGroupSchedule.model';

@Component({
    selector: 'jhi-create-tutorial-group',
    templateUrl: './create-tutorial-group.component.html',
})
export class CreateTutorialGroupComponent implements OnInit {
    tutorialGroupToCreate: TutorialGroup = new TutorialGroup();
    isLoading: boolean;
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
        });
        this.tutorialGroupToCreate = new TutorialGroup();
    }

    createTutorialGroup(formData: TutorialGroupFormData) {
        // required fields
        if (!formData?.title || !formData?.teachingAssistant) {
            return;
        }

        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, location, schedule } = formData;

        this.tutorialGroupToCreate.title = title;
        this.tutorialGroupToCreate.teachingAssistant = teachingAssistant;
        this.tutorialGroupToCreate.additionalInformation = additionalInformation;
        this.tutorialGroupToCreate.capacity = capacity;
        this.tutorialGroupToCreate.isOnline = isOnline;
        this.tutorialGroupToCreate.language = language;
        this.tutorialGroupToCreate.location = location;
        if (schedule) {
            this.tutorialGroupToCreate.tutorialGroupSchedule = new TutorialGroupSchedule();
            this.tutorialGroupToCreate.tutorialGroupSchedule.validFromInclusive = schedule.validFromInclusive ? schedule.validFromInclusive : undefined;
            this.tutorialGroupToCreate.tutorialGroupSchedule.validToInclusive = schedule.validToInclusive ? schedule.validToInclusive : undefined;
            this.tutorialGroupToCreate.tutorialGroupSchedule.dayOfWeek = schedule.dayOfWeek;
            this.tutorialGroupToCreate.tutorialGroupSchedule.startTime = schedule.startTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.endTime = schedule.endTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.repetitionFrequency = schedule.repetitionFrequency;
            this.tutorialGroupToCreate.tutorialGroupSchedule.timeZone = schedule.timeZone.tzCode;
        }

        this.isLoading = true;

        this.tutorialGroupService
            .create(this.tutorialGroupToCreate, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
