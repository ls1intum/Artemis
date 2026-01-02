import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, ViewEncapsulation, inject, input, output, signal, viewChild } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { Subject } from 'rxjs';
import { finalize, map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { CreateTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupSessionRowButtonsComponent } from './tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupSessionsTableComponent } from 'app/tutorialgroup/shared/tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { getDayTranslationKey } from 'app/tutorialgroup/shared/util/weekdays';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { DialogModule } from 'primeng/dialog';

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
        DialogModule,
        CreateTutorialGroupSessionComponent,
    ],
})
export class TutorialGroupSessionsManagementComponent implements OnDestroy {
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private calendarService = inject(CalendarService);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    readonly dialogVisible = signal<boolean>(false);
    readonly dialogClosed = output<void>();

    isLoading = false;

    faPlus = faPlus;

    readonly tutorialGroupId = input.required<number>();
    readonly course = input.required<Course>();
    tutorialGroup: TutorialGroup;
    sessions: TutorialGroupSession[] = [];
    tutorialGroupSchedule: TutorialGroupSchedule;
    attendanceUpdated = false;

    readonly createSessionDialog = viewChild<CreateTutorialGroupSessionComponent>('createSessionDialog');

    open(): void {
        this.dialogVisible.set(true);
        this.loadAll();
    }

    close(): void {
        this.dialogVisible.set(false);
        if (this.attendanceUpdated) {
            this.dialogClosed.emit();
        }
    }

    getDayTranslationKey = getDayTranslationKey;
    loadAll() {
        this.isLoading = true;
        return this.tutorialGroupService
            .getOneOfCourse(this.course().id!, this.tutorialGroupId())
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
        this.createSessionDialog()?.open();
    }

    onSessionCreated(): void {
        this.loadAll();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
