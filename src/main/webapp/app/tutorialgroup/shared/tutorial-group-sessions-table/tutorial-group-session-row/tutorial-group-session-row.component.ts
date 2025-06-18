import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostBinding, Input, TemplateRef, ViewEncapsulation, effect, inject, input, output, signal } from '@angular/core';
import { faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { map } from 'rxjs';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';

@Component({
    // this is intended and an attribute selector because otherwise the rendered table breaks
    selector: '[jhi-session-row]',
    templateUrl: './tutorial-group-session-row.component.html',
    styleUrls: ['./tutorial-group-session-row.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbPopover, FaIconComponent, FormsModule, TranslateDirective, NgTemplateOutlet, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class TutorialGroupSessionRowComponent {
    private changeDetectorRef = inject(ChangeDetectorRef);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);

    @HostBinding('class') class = 'tutorial-group-session-row';

    readonly showIdColumn = input(false);

    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.

    @Input() extraColumn: TemplateRef<any>;

    readonly session = input.required<TutorialGroupSession>();
    readonly localSession = signal<TutorialGroupSession>({} as TutorialGroupSession);
    readonly tutorialGroup = input.required<TutorialGroup>();
    readonly timeZone = input<string>();
    readonly isReadOnly = input(false);

    readonly attendanceChanged = output<TutorialGroupSession>();
    constructor() {
        effect(() => {
            const session = this.session();
            if (session) {
                this.localSession.set({ ...session });
                this.updateSomethingBasedOnSession();
            }
        });
    }

    persistedAttendanceCount?: number = undefined;
    attendanceDiffersFromPersistedValue = false;

    isUpdatingAttendance = false;

    cancellationReason?: string;
    isCancelled = false;

    overlapsWithFreePeriod = false;

    faUmbrellaBeach = faUmbrellaBeach;

    hasSchedule = false;

    updateSomethingBasedOnSession() {
        if (this.localSession()) {
            this.isCancelled = this.localSession().status === TutorialGroupSessionStatus.CANCELLED;
            this.hasSchedule = !!this.localSession().tutorialGroupSchedule;
            this.overlapsWithFreePeriod = !!this.localSession().tutorialGroupFreePeriod;
            if (this.isCancelled) {
                if (this.overlapsWithFreePeriod) {
                    this.cancellationReason = this.localSession().tutorialGroupFreePeriod?.reason ? this.localSession().tutorialGroupFreePeriod!.reason : undefined;
                } else {
                    this.cancellationReason = this.localSession().statusExplanation ? this.localSession().statusExplanation : undefined;
                }
            }
            this.persistedAttendanceCount = this.localSession().attendanceCount;
            this.attendanceDiffersFromPersistedValue = false;
            this.changeDetectorRef.detectChanges();
        }
    }

    onAttendanceInput(newAttendanceCount: number | null) {
        this.localSession().attendanceCount = newAttendanceCount === null ? undefined : newAttendanceCount;
        this.attendanceDiffersFromPersistedValue = this.persistedAttendanceCount !== this.localSession().attendanceCount;
    }

    saveAttendanceCount() {
        this.isUpdatingAttendance = true;
        this.tutorialGroupSessionService
            .updateAttendanceCount(this.tutorialGroup().course!.id!, this.tutorialGroup().id!, this.localSession().id!, this.localSession().attendanceCount!)
            .pipe(
                map((res: HttpResponse<TutorialGroupSession>) => {
                    return res.body!;
                }),
            )
            .subscribe({
                next: (tutorialGroupSession: TutorialGroupSession) => {
                    this.localSession.set(tutorialGroupSession);
                    this.persistedAttendanceCount = this.localSession().attendanceCount;
                    this.attendanceDiffersFromPersistedValue = false;
                    this.attendanceChanged.emit(this.localSession());
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.localSession().attendanceCount = this.persistedAttendanceCount;
                    this.attendanceDiffersFromPersistedValue = false;
                },
            })
            .add(() => {
                this.isUpdatingAttendance = false;
                this.changeDetectorRef.detectChanges();
            });
    }
}
