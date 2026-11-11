import { Component, computed, inject, input, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { map, tap } from 'rxjs';

import { TumAetUiButtonComponent, TumAetUiConfirmDialogComponent, TumAetUiConfirmationService, TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { onError } from 'app/foundation/util/global.utils';
import { PresentationRubricSave } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { createRubricForm, readRubricForm } from 'app/presentation/manage/presentation-rubric-form';
import { PresentationRubricEditorComponent } from 'app/presentation/manage/presentation-rubric-editor.component';

@Component({
    selector: 'jhi-presentation-rubric-dialog',
    templateUrl: './presentation-rubric-dialog.component.html',
    providers: [TumAetUiConfirmationService],
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiProgressSpinnerComponent,
        PresentationRubricEditorComponent,
    ],
})
export class PresentationRubricDialogComponent {
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly translateService = inject(TranslateService);

    readonly courseId = input.required<number>();
    readonly presentationAssessmentId = input.required<number>();
    readonly isSaving = input(false);
    readonly saved = output<PresentationRubricSave>();
    readonly cancelled = output<void>();

    private readonly rubricResource = rxResource({
        params: () => ({ courseId: this.courseId(), presentationAssessmentId: this.presentationAssessmentId() }),
        stream: ({ params }) =>
            this.presentationAssessmentService.getRubric(params.courseId, params.presentationAssessmentId).pipe(
                map((response) => response.body ?? undefined),
                tap({ error: (error: HttpErrorResponse) => onError(this.alertService, error) }),
            ),
    });
    /** Whether the errors of the whole rubric are shown, which they are only after an attempt to save. */
    protected readonly showErrors = signal(false);
    protected readonly isLoading = computed(() => this.rubricResource.isLoading());
    private readonly rubric = computed(() => (this.rubricResource.hasValue() ? this.rubricResource.value() : undefined));
    /** Assessed instances fix the structure of the rubric; only texts and weights stay editable. */
    protected readonly locked = computed(() => (this.rubric()?.assessedInstanceCount ?? 0) > 0);
    protected readonly form = computed(() => {
        const rubric = this.rubric();
        return rubric ? createRubricForm(rubric.criteria, rubric.assessedInstanceCount > 0) : undefined;
    });

    /** Saves the rubric; if new weights change the results of assessed presentations, only after a confirmation that names how many. */
    protected save(): void {
        const form = this.form();
        const rubric = this.rubric();
        if (!form || !rubric || this.isSaving()) {
            return;
        }
        if (form.invalid) {
            form.markAllAsTouched();
            this.showErrors.set(true);
            return;
        }
        const criteria = readRubricForm(form);
        const save = { criteria, version: rubric.version };
        const weightsChanged = criteria.some((criterion, position) => criterion.weight !== rubric.criteria[position]?.weight);
        if (rubric.assessedInstanceCount === 0 || !weightsChanged) {
            this.saved.emit(save);
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.presentationAssessment.rubric.weightChange.header'),
            message: this.translateService.instant('artemisApp.presentationAssessment.rubric.weightChange.message', { count: rubric.assessedInstanceCount }),
            acceptLabel: this.translateService.instant('artemisApp.presentationAssessment.rubric.weightChange.confirm'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            accept: () => this.saved.emit(save),
        });
    }

    protected cancel(): void {
        this.cancelled.emit();
    }
}
