import { Component, Input, OnDestroy } from '@angular/core';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, from } from 'rxjs';
import { finalize, map, takeUntil } from 'rxjs/operators';
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
})
export class TutorialGroupSessionsManagementComponent implements OnDestroy {
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

    isInitialized = false;

    constructor(private tutorialGroupService: TutorialGroupsService, private alertService: AlertService, private modalService: NgbModal, private activeModal: NgbActiveModal) {}

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
            });
    }

    openCreateSessionDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(CreateTutorialGroupSessionComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroup = this.tutorialGroup;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {
                this.loadAll();
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
