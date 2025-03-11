import { ChangeDetectionStrategy, Component, Input, OnDestroy, inject } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupSessionFormComponent } from '../tutorial-group-session-form/tutorial-group-session-form.component';
import { captureException } from '@sentry/angular';

@Component({
    selector: 'jhi-create-tutorial-group-session',
    templateUrl: './create-tutorial-group-session.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupSessionFormComponent],
})
export class CreateTutorialGroupSessionComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    tutorialGroupSessionToCreate: TutorialGroupSessionDTO = new TutorialGroupSessionDTO();
    isLoading: boolean;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    course: Course;

    isInitialized = false;

    initialize() {
        if (!this.course || !this.tutorialGroup) {
            captureException('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    createTutorialGroupSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        this.tutorialGroupSessionToCreate.date = date;
        this.tutorialGroupSessionToCreate.startTime = startTime;
        this.tutorialGroupSessionToCreate.endTime = endTime;
        this.tutorialGroupSessionToCreate.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .create(this.course.id!, this.tutorialGroup.id!, this.tutorialGroupSessionToCreate)
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
                    this.onError(res);
                    this.clear();
                },
            });
    }

    onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error && error.errorKey && error.errorKey === 'sessionOverlapsWithSession') {
            this.alertService.error(error.message, error.params);
        } else {
            this.alertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
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
