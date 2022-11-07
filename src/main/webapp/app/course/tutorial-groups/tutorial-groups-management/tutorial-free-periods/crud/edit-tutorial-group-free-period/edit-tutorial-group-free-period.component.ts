import { Component, Input } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-edit-tutorial-group-free-period',
    templateUrl: './edit-tutorial-group-free-period.component.html',
})
export class EditTutorialGroupFreePeriodComponent {
    isLoading = false;

    _freePeriod: TutorialGroupFreePeriod;
    @Input()
    set freePeriod(period: TutorialGroupFreePeriod) {
        this._freePeriod = period;
        if (this._freePeriod && this.course) {
            this.formData = {
                date: this._freePeriod.start?.tz(this.course.timeZone).toDate(),
                reason: this._freePeriod.reason,
            };
        }
    }

    @Input()
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;

    @Input()
    course: Course;

    formData: TutorialGroupFreePeriodFormData;
    constructor(
        private activeModal: NgbActiveModal,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private alertService: AlertService,
    ) {}

    updateTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { date, reason } = formData;

        const tutorialGroupFreePeriodDto = new TutorialGroupFreePeriodDTO();
        tutorialGroupFreePeriodDto.date = date;
        tutorialGroupFreePeriodDto.reason = reason;

        this.isLoading = true;

        this.tutorialGroupFreePeriodService
            .update(this.course.id!, this.tutorialGroupsConfiguration.id!, this._freePeriod.id!, tutorialGroupFreePeriodDto)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (res: HttpErrorResponse) => {
                    this.clear();
                    onError(this.alertService, res);
                },
            });
    }

    clear() {
        this.activeModal.dismiss();
    }
}
