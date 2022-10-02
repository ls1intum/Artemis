import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-edit-tutorial-group',
    templateUrl: './edit-tutorial-group.component.html',
})
export class EditTutorialGroupComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    formData: TutorialGroupFormData;
    courseId: number;
    tutorialGroupId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.getOneOfCourse(this.courseId, this.tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroup = tutorialGroupResult.body;
                    }
                    this.formData = {
                        title: this.tutorialGroup.title,
                        teachingAssistant: this.tutorialGroup.teachingAssistant,
                        additionalInformation: this.tutorialGroup.additionalInformation,
                        capacity: this.tutorialGroup.capacity,
                        isOnline: this.tutorialGroup.isOnline,
                        language: this.tutorialGroup.language,
                        campus: this.tutorialGroup.campus,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroup(formData: TutorialGroupFormData) {
        // required fields
        if (!formData?.title || !formData?.teachingAssistant) {
            return;
        }

        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus } = formData;
        this.tutorialGroup.title = title;
        this.tutorialGroup.teachingAssistant = teachingAssistant;
        this.tutorialGroup.additionalInformation = additionalInformation;
        this.tutorialGroup.capacity = capacity;
        this.tutorialGroup.isOnline = isOnline;
        this.tutorialGroup.language = language;
        this.tutorialGroup.campus = campus;

        this.isLoading = true;
        this.tutorialGroupService
            .update(this.courseId, this.tutorialGroupId, this.tutorialGroup)
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
