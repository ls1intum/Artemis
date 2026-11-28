import { Component, input } from '@angular/core';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons/faCheckCircle';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons/faQuestionCircle';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Two status indicators for the test case table:
 * - Are there unsaved changes?
 * - Have test cases been changed but the student submissions were not triggered?
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-status',
    template: `
        <div class="flex flex-col justify-between">
            @if (hasUnsavedTestCaseChanges() || hasUnsavedCategoryChanges()) {
                <div
                    id="test-case-status-unsaved-changes"
                    class="flex items-center rounded-md px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap text-white mb-1 bg-state-warning"
                >
                    <fa-icon class="ms-2" [icon]="faExclamationTriangle" />
                    @if (hasUnsavedTestCaseChanges() && hasUnsavedCategoryChanges()) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedChanges"></span>
                    }
                    @if (hasUnsavedTestCaseChanges() && !hasUnsavedCategoryChanges()) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedTestCaseChanges"></span>
                    }
                    @if (!hasUnsavedTestCaseChanges() && hasUnsavedCategoryChanges()) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedCategoryChanges"></span>
                    }
                </div>
            } @else {
                <div
                    id="test-case-status-no-unsaved-changes"
                    class="flex items-center rounded-md px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap text-white mb-1 bg-state-success"
                >
                    <fa-icon class="ms-2" [icon]="faCheckCircle" />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.noUnsavedChanges"></span>
                </div>
            }
            @if (exerciseIsReleasedAndHasResults()) {
                @if (hasUpdatedGradingConfig()) {
                    <div
                        id="test-case-status-updated"
                        class="flex items-center rounded-md px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap text-white bg-state-warning"
                    >
                        <fa-icon
                            class="ms-2"
                            [icon]="faExclamationTriangle"
                            [tumAetUiTooltip]="'artemisApp.programmingExercise.configureGrading.updatedGradingConfigTooltip' | artemisTranslate"
                        />
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.updatedGradingConfigShort"></span>
                    </div>
                } @else {
                    <div
                        id="test-case-status-no-updated"
                        class="flex items-center rounded-md px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap text-white bg-state-success"
                    >
                        <fa-icon class="ms-2" [icon]="faCheckCircle" />
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.noUpdatedGradingConfig"></span>
                    </div>
                }
            } @else {
                <div
                    id="test-case-status-not-released"
                    class="flex items-center rounded-md px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap text-white bg-(--secondary)"
                >
                    <fa-icon class="ms-2" [icon]="faQuestionCircle" [tumAetUiTooltip]="'artemisApp.programmingExercise.configureGrading.notReleasedTooltip' | artemisTranslate" />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.notReleased"></span>
                </div>
            }
        </div>
    `,
    imports: [FaIconComponent, TranslateDirective, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class ProgrammingExerciseConfigureGradingStatusComponent {
    readonly exerciseIsReleasedAndHasResults = input.required<boolean>();
    readonly hasUnsavedTestCaseChanges = input.required<boolean>();
    readonly hasUnsavedCategoryChanges = input.required<boolean>();
    readonly hasUpdatedGradingConfig = input.required<boolean>();

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faQuestionCircle = faQuestionCircle;
}
