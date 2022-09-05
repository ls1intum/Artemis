import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { SortService } from 'app/shared/service/sort.service';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CancellationModalComponent } from 'app/course/tutorial-groups/schedule-management/cancellation-modal/cancellation-modal.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-session-management',
    templateUrl: './session-management.component.html',
    styleUrls: ['./session-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class SessionManagementComponent implements OnInit {
    isLoading = false;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private alertService: AlertService,
        private sortService: SortService,
        private changeDetectorRef: ChangeDetectorRef,
        private modalService: NgbModal,
    ) {}

    faPlus = faPlus;

    courseId: number;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    tutorialGroupSessions: TutorialGroupSession[] = [];

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;

    openCancellationModal(session: TutorialGroupSession): void {
        const modalRef = this.modalService.open(CancellationModalComponent);
        modalRef.componentInstance.tutorialGroupSession = session;
        modalRef.result.then((result) => {
            if (result === 'confirmed') {
                this.loadAll();
            }
        });
    }

    determineRowClass(session: TutorialGroupSession): string {
        if (session.status === TutorialGroupSessionStatus.CANCELLED) {
            return 'table-danger';
        }
        if (!session.tutorialGroupSchedule) {
            return 'table-warning';
        }
        return '';
    }

    ngOnInit(): void {
        this.loadAll();
    }

    private loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.getOne(tutorialGroupId).pipe(finalize(() => (this.isLoading = false)));
                }),
                map((res: HttpResponse<TutorialGroup>) => {
                    return res.body;
                }),
            )
            .subscribe({
                next: (tutorialGroup) => {
                    if (tutorialGroup) {
                        this.tutorialGroup = tutorialGroup;
                        if (tutorialGroup.tutorialGroupSessions) {
                            this.tutorialGroupSessions = this.sortService.sortByProperty(tutorialGroup.tutorialGroupSessions, 'start', true);
                        }
                        if (tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = tutorialGroup.tutorialGroupSchedule;
                        }
                        if (tutorialGroup.course?.tutorialGroupsConfiguration) {
                            this.tutorialGroupsConfiguration = tutorialGroup.course.tutorialGroupsConfiguration;
                            // convert sessions to the configured timezone for easier management
                            this.tutorialGroupSessions.forEach((session) => {
                                session.start = session.start?.tz(this.tutorialGroupsConfiguration.timeZone);
                                session.end = session.end?.tz(this.tutorialGroupsConfiguration.timeZone);
                            });
                        }
                    }
                    this.isLoading = false;
                    this.changeDetectorRef.detectChanges();
                },
                error: (res: HttpErrorResponse) => {
                    this.isLoading = false;
                    onError(this.alertService, res);
                },
            });
    }
}
