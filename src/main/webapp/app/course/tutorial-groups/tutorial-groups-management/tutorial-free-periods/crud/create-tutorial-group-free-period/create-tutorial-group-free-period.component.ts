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
        const { date, reason } = formData;

        this.tutorialGroupFreePeriodToCreate.date = date;
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

    clear() {
        this.activeModal.dismiss();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
