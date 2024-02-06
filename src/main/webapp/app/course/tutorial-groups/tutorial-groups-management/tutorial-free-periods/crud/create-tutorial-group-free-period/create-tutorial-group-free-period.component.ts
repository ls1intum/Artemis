import { ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-create-tutorial-group-free-day',
    templateUrl: './create-tutorial-group-free-period.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTutorialGroupFreePeriodComponent implements OnDestroy {
    ngUnsubscribe = new Subject<void>();

    tutorialGroupFreePeriodToCreate: TutorialGroupFreePeriodDTO = new TutorialGroupFreePeriodDTO();
    isLoading: boolean;

    @Input()
    tutorialGroupConfigurationId: number;

    @Input()
    course: Course;

    isInitialized = false;

    constructor(
        private activeModal: NgbActiveModal,
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private alertService: AlertService,
    ) {}

    initialize() {
        if (!this.tutorialGroupConfigurationId || !this.course) {
            console.error('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
        }
    }
    createTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { startDate, endDate, startTime, endTime, reason } = formData;

        this.tutorialGroupFreePeriodToCreate.startDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        this.tutorialGroupFreePeriodToCreate.endDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        this.tutorialGroupFreePeriodToCreate.reason = reason;

        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .create(this.course.id!, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.clear();
                },
            });
    }

    public static combineDateAndTimeWithAlternativeDate(date?: Date, time?: Date, alternativeDate?: Date): Date {
        // If date is undefined, the resulting date is the endDate of a tutorialFreeDay
        if (!date) {
            if (!alternativeDate) {
                throw new Error('date and time are undefined');
            } else if (!time) {
                const resDate = new Date(alternativeDate);
                resDate.setHours(23, 59);
                return resDate;
            } else {
                const resDate = new Date(alternativeDate);
                resDate.setHours(time.getHours(), time.getMinutes());
                return resDate;
            }
        } else if (alternativeDate) {
            // if there is a date and an alternative date, the resulting date is the endDate of a tutorialFreePeriod
            date.setHours(23, 59);
            return date;
        } else if (!time) {
            // if there is a date and no time, the resulting date is the startDate of a tutorialFreeDay or a tutorialFreePeriod
            date.setHours(0, 0, 0);
            return date;
        } else {
            // if there is a date and a time, the resulting date is the startDate or endDate of a tutorialFreePeriodWithinDay
            date.setHours(time.getHours(), time.getMinutes());
            return date;
        }
    }

    clear() {
        this.activeModal.dismiss();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
