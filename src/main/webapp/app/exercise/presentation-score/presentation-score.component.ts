import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';

import { Component, DoCheck, OnDestroy, inject, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { GradeStepApi } from 'app/openapi/api/grade-step-api';
import { Subscription } from 'rxjs';
import type { GradeSteps } from 'app/openapi/model/grade-steps';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-presentation-score-checkbox',
    template: `
        <ng-container *jhiHasAnyAuthority="IS_AT_LEAST_EDITOR">
            @if (this.showPresentationScoreCheckbox()) {
                <div class="flex flex-col gap-1 mb-4!">
                    <div class="flex items-center gap-2">
                        <tumaet-ui-checkbox
                            inputId="field_presentationScoreEnabled"
                            name="presentationScoreEnabled"
                            [ngModel]="exercise().presentationScoreEnabled"
                            (ngModelChange)="exercise().presentationScoreEnabled = $event"
                        />
                        <label class="" for="field_presentationScoreEnabled" jhiTranslate="artemisApp.exercise.presentationScoreEnabled.title">Presentation Score Enabled</label>
                        <fa-icon
                            [icon]="faQuestionCircle"
                            class="text-muted-color"
                            tumAetUiTooltip="{{ 'artemisApp.exercise.presentationScoreEnabled.description' | artemisTranslate }}"
                        />
                    </div>
                </div>
            }
        </ng-container>
    `,
    imports: [TumAetUiCheckboxComponent, TumAetUiTooltipDirective, HasAnyAuthorityDirective, FormsModule, TranslateDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class PresentationScoreComponent implements DoCheck, OnDestroy {
    private readonly gradeStepApi = inject(GradeStepApi);

    protected readonly faQuestionCircle = faQuestionCircle;

    protected readonly IS_AT_LEAST_EDITOR = IS_AT_LEAST_EDITOR;

    readonly exercise = input<Exercise>(undefined!);

    private gradeSteps?: GradeSteps;
    private gradeStepsSub?: Subscription;

    ngDoCheck(): void {
        const exercise = this.exercise();
        if (!this.gradeStepsSub && exercise.course?.id) {
            this.gradeStepsSub = this.gradeStepApi.getAllGradeStepsForCourse(exercise.course.id).subscribe((gradeSteps) => {
                if (gradeSteps) {
                    this.gradeSteps = gradeSteps;
                }
            });
        }
    }

    ngOnDestroy(): void {
        if (this.gradeStepsSub) {
            this.gradeStepsSub.unsubscribe();
        }
    }

    showPresentationScoreCheckbox(): boolean {
        return this.isBasicPresentation() || this.isGradedPresentation();
    }

    private isBasicPresentation(): boolean {
        return !!this.exercise().course?.presentationScore;
    }

    private isGradedPresentation(): boolean {
        return !!(this.exercise().course && (this.gradeSteps?.presentationsNumber ?? 0) > 0);
    }
}
