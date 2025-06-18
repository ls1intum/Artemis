import { ChangeDetectionStrategy, Component, OnDestroy, inject, input } from '@angular/core';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { AlertService } from 'app/shared/service/alert.service';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupSessionFormComponent } from '../tutorial-group-session-form/tutorial-group-session-form.component';
import { captureException } from '@sentry/angular';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';

@Component({
    selector: 'jhi-edit-tutorial-group-session',
    templateUrl: './edit-tutorial-group-session.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupSessionFormComponent],
})
export class EditTutorialGroupSessionComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly course = input.required<Course>();

    readonly tutorialGroupSession = input.required<TutorialGroupSession>();

    isLoading = false;
    formData?: TutorialGroupSessionFormData = undefined;

    isInitialized = false;

    initialize() {
        const tutorialGroupSession = this.tutorialGroupSession();
        const course = this.course();
        if (!tutorialGroupSession || !course || !this.tutorialGroup()) {
            captureException('Error: Component not fully configured');
        } else {
            this.formData = {
                date: tutorialGroupSession.start?.tz(course.timeZone).toDate(),
                startTime: tutorialGroupSession.start?.tz(course.timeZone).format('HH:mm:ss'),
                endTime: tutorialGroupSession.end?.tz(course.timeZone).format('HH:mm:ss'),
                location: tutorialGroupSession.location,
            };
            this.isInitialized = true;
        }
    }

    updateSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        const tutorialGroupSessionDTO = new TutorialGroupSessionDTO();

        tutorialGroupSessionDTO.date = date;
        tutorialGroupSessionDTO.startTime = startTime;
        tutorialGroupSessionDTO.endTime = endTime;
        tutorialGroupSessionDTO.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .update(this.course().id!, this.tutorialGroup().id!, this.tutorialGroupSession().id!, tutorialGroupSessionDTO)
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
