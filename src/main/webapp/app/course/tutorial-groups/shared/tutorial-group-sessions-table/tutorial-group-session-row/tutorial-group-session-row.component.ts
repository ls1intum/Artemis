import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, HostBinding, Input, OnChanges, Output, TemplateRef, ViewEncapsulation, inject } from '@angular/core';
import { faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { map } from 'rxjs';

@Component({
    // this is intended and an attribute selector because otherwise the rendered table breaks
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
    styleUrls: ['./tutorial-group-session-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupSessionRowComponent implements OnChanges {
    private changeDetectorRef = inject(ChangeDetectorRef);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);

    @HostBinding('class') class = 'tutorial-group-session-row';

    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() session: TutorialGroupSession;
    @Input() tutorialGroup: TutorialGroup;

    @Input() timeZone?: string = undefined;

    @Input() isReadOnly = false;

    @Output() attendanceChanged = new EventEmitter<TutorialGroupSession>();

    persistedAttendanceCount?: number = undefined;
    attendanceDiffersFromPersistedValue = false;

    isUpdatingAttendance = false;

    cancellationReason?: string;
    isCancelled = false;

    overlapsWithFreePeriod = false;

    faUmbrellaBeach = faUmbrellaBeach;

    hasSchedule = false;

    ngOnChanges() {
        if (this.session) {
            this.isCancelled = this.session.status === TutorialGroupSessionStatus.CANCELLED;
            this.hasSchedule = !!this.session.tutorialGroupSchedule;
            this.overlapsWithFreePeriod = !!this.session.tutorialGroupFreePeriod;
            if (this.isCancelled) {
                if (this.overlapsWithFreePeriod) {
                    this.cancellationReason = this.session.tutorialGroupFreePeriod?.reason ? this.session.tutorialGroupFreePeriod.reason : undefined;
                } else {
                    this.cancellationReason = this.session.statusExplanation ? this.session.statusExplanation : undefined;
                }
            }
            this.persistedAttendanceCount = this.session.attendanceCount;
            this.attendanceDiffersFromPersistedValue = false;
            this.changeDetectorRef.detectChanges();
        }
    }

    onAttendanceInput(newAttendanceCount: number | null) {
        this.session.attendanceCount = newAttendanceCount === null ? undefined : newAttendanceCount;
        this.attendanceDiffersFromPersistedValue = this.persistedAttendanceCount !== this.session.attendanceCount;
    }

    saveAttendanceCount() {
        this.isUpdatingAttendance = true;
        this.tutorialGroupSessionService
            .updateAttendanceCount(this.tutorialGroup.course!.id!, this.tutorialGroup.id!, this.session.id!, this.session.attendanceCount!)
            .pipe(
                map((res: HttpResponse<TutorialGroupSession>) => {
                    return res.body!;
                }),
            )
            .subscribe({
                next: (tutorialGroupSession: TutorialGroupSession) => {
                    this.session = tutorialGroupSession;
                    this.persistedAttendanceCount = this.session.attendanceCount;
                    this.attendanceDiffersFromPersistedValue = false;
                    this.attendanceChanged.emit(this.session);
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.session.attendanceCount = this.persistedAttendanceCount;
                    this.attendanceDiffersFromPersistedValue = false;
                },
            })
            .add(() => {
                this.isUpdatingAttendance = false;
                this.changeDetectorRef.detectChanges();
            });
    }
}
