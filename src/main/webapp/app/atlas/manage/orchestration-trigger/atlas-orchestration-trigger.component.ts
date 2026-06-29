import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { TooltipModule } from 'primeng/tooltip';

import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CompetencyOrchestrationApiService } from 'app/atlas/shared/services/competency-orchestration-api.service';
import { AppliedActionDTO, CompetencyOrchestrationResultDTO, CompetencyOrchestrationStatus } from 'app/atlas/shared/dto/competency-orchestration-dto';
import { OrchestrationResultDialogComponent } from 'app/atlas/shared/orchestration-result-dialog/orchestration-result-dialog.component';

/**
 * Instructor-facing trigger for the manual Atlas competency orchestrator on a programming exercise.
 * Encapsulates the orchestrator button, the run lifecycle (calling {@link CompetencyOrchestrationApiService})
 * and the result dialog so that host components (e.g. the programming exercise detail page) stay decoupled
 * from Atlas-specific logic.
 */
@Component({
    selector: 'jhi-atlas-orchestration-trigger',
    templateUrl: './atlas-orchestration-trigger.component.html',
    imports: [FaIconComponent, TooltipModule, FeatureToggleHideDirective, TranslateDirective, ArtemisTranslatePipe, OrchestrationResultDialogComponent],
})
export class AtlasOrchestrationTriggerComponent {
    private readonly competencyOrchestrationApiService = inject(CompetencyOrchestrationApiService);
    private readonly alertService = inject(AlertService);

    readonly programmingExercise = input.required<ProgrammingExercise>();

    protected readonly orchestrationDialogVisible = signal(false);
    protected readonly orchestrationDialogMessage = signal('');
    protected readonly orchestrationDialogActions = signal<AppliedActionDTO[]>([]);
    protected readonly orchestrationRunning = signal(false);

    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly FeatureToggle = FeatureToggle;

    async triggerAtlasOrchestrator() {
        const exerciseId = this.programmingExercise()?.id;
        if (!exerciseId || this.orchestrationRunning()) {
            return;
        }
        this.orchestrationRunning.set(true);
        try {
            // Backend returns 2xx only for SUCCESS; IN_PROGRESS (409) and FAILED (422/500/502/503)
            // surface as HttpErrorResponse and are handled in the catch block below.
            const result = await this.competencyOrchestrationApiService.runForProgrammingExercise(exerciseId);
            // PARTIAL responds with 207 (MULTI_STATUS, still 2xx), so both SUCCESS and PARTIAL land here.
            // summary/appliedActions may be omitted from the response when empty (@JsonInclude(NON_EMPTY)).
            const summary = result.summary?.trim() ?? '';
            this.orchestrationDialogMessage.set(summary);
            this.orchestrationDialogActions.set(result.appliedActions ?? []);
            this.orchestrationDialogVisible.set(true);
            if (result.status === CompetencyOrchestrationStatus.Partial) {
                this.alertService.addAlert({
                    type: AlertType.WARNING,
                    message: summary || 'artemisApp.atlasOrchestrator.partial',
                    disableTranslation: summary.length > 0,
                });
            }
        } catch (err) {
            const httpErr = err as HttpErrorResponse;
            const body = httpErr?.error as CompetencyOrchestrationResultDTO | undefined;
            const summary = body?.summary?.trim() || '';
            if (httpErr?.status === 409) {
                this.alertService.warning('artemisApp.atlasOrchestrator.inProgress');
            } else if (httpErr?.status === 422 || httpErr?.status === 500 || httpErr?.status === 502 || httpErr?.status === 503) {
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: summary || 'artemisApp.atlasOrchestrator.error',
                    disableTranslation: summary.length > 0,
                });
            } else {
                onError(this.alertService, httpErr);
            }
        } finally {
            this.orchestrationRunning.set(false);
        }
    }
}
