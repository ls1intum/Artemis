import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    HostBinding,
    Input,
    Signal,
    TemplateRef,
    ViewEncapsulation,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
} from '@angular/core';
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

    // TODO: Skipped for now
    @Input() extraColumn: TemplateRef<any>;

    readonly session = input.required<TutorialGroupSession>();
    readonly localSession = signal<TutorialGroupSession>({} as TutorialGroupSession);
    readonly tutorialGroup = input.required<TutorialGroup>();
    readonly timeZone = input<string>();
    readonly isReadOnly = input(false);

    readonly attendanceChanged = output<TutorialGroupSession>();

    persistedAttendanceCount?: number = undefined;

    readonly attendanceDiffersFromPersistedValue: Signal<boolean> = computed(() => this.localSession().attendanceCount !== this.persistedAttendanceCount);
    isUpdatingAttendance = false;

    cancellationReason?: string;
    isCancelled = false;

    overlapsWithFreePeriod = false;

    faUmbrellaBeach = faUmbrellaBeach;

    hasSchedule = false;

    private initialized = false;
    constructor() {
        effect(() => {
            const session = this.session();
            if (session) {
                if (!this.initialized) {
                    this.initialized = true;
                    this.updateSomethingBasedOnSession();
                }
                this.localSession.set(Object.assign({}, session));
                this.persistedAttendanceCount = session.attendanceCount ?? 0;
            }
        });

        effect(() => {
            this.localSession();
            this.updateSomethingBasedOnSession();
        });
    }

    updateSomethingBasedOnSession() {
        if (this.localSession()) {
            this.isCancelled = this.localSession().status === TutorialGroupSessionStatus.CANCELLED;
            this.hasSchedule = !!this.localSession().tutorialGroupSchedule;
            this.overlapsWithFreePeriod = !!this.localSession().tutorialGroupFreePeriod;
            if (this.isCancelled) {
                if (this.overlapsWithFreePeriod) {
                    this.cancellationReason = this.localSession().tutorialGroupFreePeriod?.reason || undefined;
                } else {
                    this.cancellationReason = this.localSession().statusExplanation || undefined;
                }
            }
        }
    }

    onAttendanceInput(newAttendanceCount: number | null) {
        this.localSession.update((session) => Object.assign({}, session, { attendanceCount: newAttendanceCount === null ? undefined : newAttendanceCount }));
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
                    this.attendanceChanged.emit(this.localSession());
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.localSession.update((session) => Object.assign({}, session, { attendanceCount: this.persistedAttendanceCount }));
                },
            })
            .add(() => {
                this.isUpdatingAttendance = false;
                this.changeDetectorRef.detectChanges();
            });
    }
}
