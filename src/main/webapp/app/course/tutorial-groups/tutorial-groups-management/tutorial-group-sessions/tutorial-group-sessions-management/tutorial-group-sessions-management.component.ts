import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, ViewEncapsulation, inject } from '@angular/core';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { EMPTY, Subject, from } from 'rxjs';
import { catchError, finalize, map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { getDayTranslationKey } from 'app/course/tutorial-groups/shared/weekdays';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';

@Component({
    selector: 'jhi-session-management',
    templateUrl: './tutorial-group-sessions-management.component.html',
    styleUrls: ['./tutorial-group-sessions-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class TutorialGroupSessionsManagementComponent implements OnDestroy {
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private activeModal = inject(NgbActiveModal);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    isLoading = false;

    faPlus = faPlus;

    @Input()
    tutorialGroupId: number;
    @Input()
    course: Course;
    tutorialGroup: TutorialGroup;
    sessions: TutorialGroupSession[] = [];
    tutorialGroupSchedule: TutorialGroupSchedule;
    attendanceUpdated = false;

    isInitialized = false;

    initialize() {
        if (!this.tutorialGroupId || !this.course) {
            console.error('Error: Component not fully configured');
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
                        if (tutorialGroup.tutorialGroupSessions) {
                            this.sessions = tutorialGroup.tutorialGroupSessions;
                        }
                        if (tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = tutorialGroup.tutorialGroupSchedule;
                        }
                    }
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
