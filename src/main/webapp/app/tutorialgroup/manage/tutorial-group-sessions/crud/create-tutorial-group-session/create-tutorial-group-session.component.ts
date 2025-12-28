import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { AlertService } from 'app/shared/service/alert.service';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupSessionFormData } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject } from 'rxjs';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupSessionFormComponent } from '../tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-create-tutorial-group-session',
    templateUrl: './create-tutorial-group-session.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TutorialGroupSessionFormComponent, DialogModule, ArtemisTranslatePipe],
})
export class CreateTutorialGroupSessionComponent implements OnDestroy {
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    readonly dialogVisible = signal<boolean>(false);
    readonly sessionCreated = output<void>();

    tutorialGroupSessionToCreate: TutorialGroupSessionDTO = new TutorialGroupSessionDTO();
    isLoading = false;

    readonly tutorialGroup = input.required<TutorialGroup>();
    readonly course = input.required<Course>();

    open(): void {
        this.tutorialGroupSessionToCreate = new TutorialGroupSessionDTO();
        this.dialogVisible.set(true);
    }

    close(): void {
        this.dialogVisible.set(false);
    }

    createTutorialGroupSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        this.tutorialGroupSessionToCreate.date = date;
        this.tutorialGroupSessionToCreate.startTime = startTime;
        this.tutorialGroupSessionToCreate.endTime = endTime;
        this.tutorialGroupSessionToCreate.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .create(this.course().id!, this.tutorialGroup().id!, this.tutorialGroupSessionToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.close();
                    this.sessionCreated.emit();
                },
                error: (res: HttpErrorResponse) => {
                    this.onError(res);
                    this.close();
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

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
