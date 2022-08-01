import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group.model';
import { TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
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

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private tutorialGroupService: TutorialGroupsService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.findById(tutorialGroupId, this.courseId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroup = tutorialGroupResult.body;
                        // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                        if (!this.tutorialGroup.registeredStudents) {
                            this.tutorialGroup.registeredStudents = [];
                        }
                    }
                    this.formData = {
                        title: this.tutorialGroup.title,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroup(formData: TutorialGroupFormData) {
        const { title } = formData;
        this.tutorialGroup.title = title;

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
