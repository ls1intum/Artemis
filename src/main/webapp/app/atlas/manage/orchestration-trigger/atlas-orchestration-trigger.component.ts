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
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { CompetencyOrchestrationApiService } from 'app/atlas/shared/services/competency-orchestration-api.service';
import { AppliedActionDTO, CompetencyOrchestrationResultDTO, CompetencyOrchestrationStatus } from 'app/atlas/shared/dto/competency-orchestration-dto';
import { OrchestrationResultDialogComponent } from 'app/atlas/shared/orchestration-result-dialog/orchestration-result-dialog.component';

/**
 * Instructor-facing trigger for the manual Atlas competency orchestrator on an exercise.
 * Encapsulates the orchestrator button, the run lifecycle (calling {@link CompetencyOrchestrationApiService})
 * and the result dialog so that host components (e.g. the exercise detail pages) stay decoupled
 * from Atlas-specific logic. Works for any supported exercise type (programming, text, modeling,
 * file-upload, quiz); the backend resolves the exercise generically.
 */
@Component({
    selector: 'jhi-atlas-orchestration-trigger',
    templateUrl: './atlas-orchestration-trigger.component.html',
    // Lay the host out like a bare inline-block button so it aligns with sibling buttons in inline
    // (non-flex) action bars, e.g. the programming exercise detail toolbar. In flex containers the host
    // is a flex item and this display value is ignored, so projected usages are unaffected.
    styles: ':host { display: inline-block; vertical-align: top; }',
    imports: [FaIconComponent, TooltipModule, FeatureToggleHideDirective, TranslateDirective, ArtemisTranslatePipe, OrchestrationResultDialogComponent],
})
export class AtlasOrchestrationTriggerComponent {
    private readonly competencyOrchestrationApiService = inject(CompetencyOrchestrationApiService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    readonly exercise = input<Exercise>();
    readonly lectureUnitId = input<number>();
    /** Button CSS classes, so each host page can match its own action-bar styling (solid vs outline). */
    readonly buttonClass = input<string>('btn btn-primary btn-sm');
    readonly showLabel = input(true);

    /**
     * Whether the Atlas module is enabled on this instance. Owned here so host pages stay free of Atlas
     * knowledge: a host only decides instructor / non-exam visibility, and this component self-hides when
     * the module is off (the {@code AtlasAgent} feature toggle is a separate, finer runtime gate on the button).
     */
    protected readonly atlasModuleActive = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);

    protected readonly orchestrationDialogVisible = signal(false);
    protected readonly orchestrationDialogMessage = signal('');
    protected readonly orchestrationDialogActions = signal<AppliedActionDTO[]>([]);
    protected readonly orchestrationRunning = signal(false);

    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly FeatureToggle = FeatureToggle;

    async triggerAtlasOrchestrator() {
        const exerciseId = this.exercise()?.id;
        const lectureUnitId = this.lectureUnitId();
        if ((!exerciseId && !lectureUnitId) || (exerciseId && lectureUnitId) || this.orchestrationRunning()) {
            return;
        }
        this.orchestrationRunning.set(true);
        try {
            // Backend returns 2xx only for SUCCESS; IN_PROGRESS (409) and FAILED (422/500/502/503)
            // surface as HttpErrorResponse and are handled in the catch block below.
            const result = exerciseId
                ? await this.competencyOrchestrationApiService.runForExercise(exerciseId)
                : await this.competencyOrchestrationApiService.runForLectureUnit(lectureUnitId!);
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
