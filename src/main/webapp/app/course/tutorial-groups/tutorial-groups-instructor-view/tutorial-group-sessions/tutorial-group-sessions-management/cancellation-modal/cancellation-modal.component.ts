import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-cancellation-modal',
    templateUrl: './cancellation-modal.component.html',
    styleUrls: ['./cancellation-modal.component.scss'],
})
export class CancellationModalComponent implements OnInit {
    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    form: FormGroup;

    @Input()
    courseId: number;

    @Input()
    tutorialGroupId: number;

    @Input()
    tutorialGroupSession: TutorialGroupSession;
    @Input()
    refreshCallback: () => void;

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
            return tutorialGroupSession.start.format('LLLL') + ' - ' + tutorialGroupSession.end.format('LT');
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
        this.tutorialGroupSessionService.cancel(this.courseId, this.tutorialGroupId, this.tutorialGroupSession.id!, this.reasonControl?.value).subscribe({
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
        this.tutorialGroupSessionService.activate(this.courseId, this.tutorialGroupId, this.tutorialGroupSession.id!).subscribe({
            next: () => {
                this.activeModal.close('confirmed');
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
                this.activeModal.close('error');
            },
        });
    }
}
