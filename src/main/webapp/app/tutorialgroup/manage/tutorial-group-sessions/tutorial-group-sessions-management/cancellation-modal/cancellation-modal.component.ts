import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { TutorialGroupSessionDTO, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { DialogModule } from 'primeng/dialog';
import { ButtonDirective } from 'primeng/button';

@Component({
    selector: 'jhi-cancellation-modal',
    templateUrl: './cancellation-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe, DialogModule, ButtonDirective],
})
export class CancellationModalComponent implements OnInit, OnDestroy {
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);
    private fb = inject(FormBuilder);

    ngUnsubscribe = new Subject<void>();

    readonly dialogVisible = signal<boolean>(false);
    readonly confirmed = output<void>();

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;
    form: FormGroup;

    readonly course = input.required<Course>();
    readonly tutorialGroupId = input.required<number>();
    readonly tutorialGroupSession = input.required<TutorialGroupSessionDTO>();

    ngOnInit(): void {
        this.initializeForm();
    }

    open(): void {
        this.initializeForm();
        this.dialogVisible.set(true);
    }

    close(): void {
        this.dialogVisible.set(false);
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

    generateSessionLabel(tutorialGroupSession: TutorialGroupSessionDTO): string {
        if (!tutorialGroupSession?.startDate || !tutorialGroupSession?.endDate) {
            return '';
        }

        return tutorialGroupSession.startDate.tz(this.course().timeZone).format('LLLL') + ' - ' + tutorialGroupSession.endDate.tz(this.course().timeZone).format('LT');
    }

    cancelOrActivate(): void {
        if (!this.tutorialGroupSession().isCancelled) {
            this.cancelSession();
        } else {
            this.activateSession();
        }
    }

    cancelSession(): void {
        this.tutorialGroupSessionService
            .cancel(this.course().id!, this.tutorialGroupId(), this.tutorialGroupSession().id!, this.reasonControl?.value)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.close();
                    this.confirmed.emit();
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.close();
                },
            });
    }

    activateSession(): void {
        this.tutorialGroupSessionService
            .activate(this.course().id!, this.tutorialGroupId(), this.tutorialGroupSession().id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.close();
                    this.confirmed.emit();
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.close();
                },
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
