import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, ViewEncapsulation, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, finalize, map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupSessionRowButtonsComponent } from './tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { captureException } from '@sentry/angular';
import { TutorialGroupSessionsTableComponent } from 'app/tutorialgroup/shared/tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { getDayTranslationKey } from 'app/tutorialgroup/shared/util/weekdays';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-session-management',
    templateUrl: './tutorial-group-sessions-management.component.html',
    styleUrls: ['./tutorial-group-sessions-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    imports: [
        LoadingIndicatorContainerComponent,
        TranslateDirective,
        FaIconComponent,
        TutorialGroupSessionsTableComponent,
        TutorialGroupSessionRowButtonsComponent,
        ArtemisTranslatePipe,
        RemoveSecondsPipe,
    ],
})
export class TutorialGroupSessionsManagementComponent implements OnDestroy {
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private calendarService = inject(CalendarService);
    private modalService = inject(NgbModal);
    private activeModal = inject(NgbActiveModal);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    isLoading = false;

    faPlus = faPlus;
    // Need to stick to @Input due to modelRef see https://github.com/ng-bootstrap/ng-bootstrap/issues/4688
    @Input() tutorialGroupId: number;
    @Input() course: Course;
    tutorialGroup: TutorialGroup;
    sessions: TutorialGroupSession[] = [];
    tutorialGroupSchedule: TutorialGroupSchedule;
    attendanceUpdated = false;

    isInitialized = false;

    initialize() {
        if (!this.tutorialGroupId || !this.course) {
            captureException('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
            this.loadAll();
        }
    }

    getDayTranslationKey = getDayTranslationKey;
    loadAll() {
        this.isLoading = true;
        return this.tutorialGroupService
            .getOneOfCourse(this.course.id!, this.tutorialGroupId)
            .pipe(
                finalize(() => (this.isLoading = false)),
                map((res: HttpResponse<TutorialGroup>) => {
                    return res.body;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroup) => {
                    if (tutorialGroup) {
                        this.tutorialGroup = tutorialGroup;
                        this.sessions = tutorialGroup.tutorialGroupSessions ?? [];
                        if (tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = tutorialGroup.tutorialGroupSchedule;
                        }
                    }
                    this.calendarService.reloadEvents();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    openCreateSessionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(CreateTutorialGroupSessionComponent, { size: 'xl', scrollable: false, backdrop: 'static', animation: false });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroup = this.tutorialGroup;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.loadAll();
            });
    }

    clear() {
        if (this.attendanceUpdated) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
