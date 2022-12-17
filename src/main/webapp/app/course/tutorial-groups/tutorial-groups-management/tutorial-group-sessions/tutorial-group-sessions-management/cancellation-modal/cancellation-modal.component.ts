import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Course } from 'app/entities/course.model';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-cancellation-modal',
    templateUrl: './cancellation-modal.component.html',
})
export class CancellationModalComponent implements OnInit, OnDestroy {
    ngUnsubscribe = new Subject<void>();

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    form: FormGroup;

    @Input()
    course: Course;

    @Input()
    tutorialGroupId: number;

    @Input()
    tutorialGroupSession: TutorialGroupSession;

    constructor(
        public activeModal: NgbActiveModal,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private alertService: AlertService,
        private fb: FormBuilder,
    ) {}

    ngOnInit(): void {
        this.initializeForm();
    }

    get reasonControl() {
        return this.form.get('reason');
    }

    private initializeForm() {
        this.form = this.fb.group({
            reason: [undefined, [Validators.maxLength(255)]],
        });
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    generateSessionLabel(tutorialGroupSession: TutorialGroupSession): string {
        if (!tutorialGroupSession?.start || !tutorialGroupSession?.end) {
            return '';
        } else {
            return tutorialGroupSession.start.tz(this.course.timeZone).format('LLLL') + ' - ' + tutorialGroupSession.end.tz(this.course.timeZone).format('LT');
        }
    }
    cancelOrActivate(): void {
        if (this.tutorialGroupSession.status === TutorialGroupSessionStatus.ACTIVE) {
            this.cancelSession();
        } else {
            this.activateSession();
        }
    }

    cancelSession(): void {
        this.tutorialGroupSessionService
            .cancel(this.course.id!, this.tutorialGroupId, this.tutorialGroupSession.id!, this.reasonControl?.value)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.activeModal.close('confirmed');
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.activeModal.close('error');
                },
            });
    }

    activateSession(): void {
        this.tutorialGroupSessionService
            .activate(this.course.id!, this.tutorialGroupId, this.tutorialGroupSession.id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.activeModal.close('confirmed');
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.activeModal.close('error');
                },
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
