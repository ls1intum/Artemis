import { Component, OnInit, inject, input, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TextareaModule } from 'primeng/textarea';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Minimal scaffold editor for math exercises: it loads the participation's latest submission and lets the
 * student persist an opaque {@code content} payload. The interactive derivation workspace is layered on later.
 */
@Component({
    selector: 'jhi-math-submission',
    templateUrl: './math-submission.component.html',
    styleUrls: ['./math-submission.component.scss'],
    imports: [FormsModule, TranslateDirective, ButtonModule, CardModule, TextareaModule, ConfirmDialog],
    providers: [ConfirmationService],
})
export class MathSubmissionComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private mathSubmissionService = inject(MathSubmissionService);
    private alertService = inject(AlertService);
    private confirmationService = inject(ConfirmationService);
    private translatePipe = inject(ArtemisTranslatePipe);

    participationId = input<number>();

    submission = signal<MathSubmission | undefined>(undefined);
    exercise = signal<MathExercise | undefined>(undefined);
    content = signal<string>('');
    isSaving = signal(false);

    ngOnInit() {
        const participationIdParam = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
        if (participationIdParam === undefined || Number.isNaN(participationIdParam)) {
            return;
        }
        this.mathSubmissionService.getDataForMathEditor(participationIdParam).subscribe({
            next: (response) => {
                const submission = response.body ?? new MathSubmission();
                this.submission.set(submission);
                this.exercise.set(submission.participation?.exercise);
                this.content.set(submission.content ?? '');
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    save() {
        this.persist(false);
    }

    submit() {
        // Guard against silently overwriting a solution that was already submitted: ask for confirmation first.
        if (this.submission()?.submitted) {
            this.confirmationService.confirm({
                header: this.translatePipe.transform('artemisApp.mathExercise.resubmitConfirm.title'),
                message: this.translatePipe.transform('artemisApp.mathExercise.resubmitConfirm.message'),
                acceptLabel: this.translatePipe.transform('global.form.confirm'),
                rejectLabel: this.translatePipe.transform('global.form.cancel'),
                rejectButtonStyleClass: 'p-button-outlined p-button-secondary',
                accept: () => this.persist(true),
            });
            return;
        }
        this.persist(true);
    }

    private persist(submitted: boolean) {
        const exercise = this.exercise();
        if (!exercise?.id) {
            return;
        }
        const submission = this.submission() ?? new MathSubmission();
        submission.content = this.content();
        submission.submitted = submitted;
        this.isSaving.set(true);
        this.mathSubmissionService.update(submission, exercise.id).subscribe({
            next: (response) => {
                if (response.body) {
                    this.submission.set(response.body);
                    this.content.set(response.body.content ?? '');
                }
                this.isSaving.set(false);
                this.alertService.success(submitted ? 'artemisApp.mathExercise.submitSuccessful' : 'artemisApp.mathExercise.saveSuccessful');
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                onError(this.alertService, error);
            },
        });
    }
}
