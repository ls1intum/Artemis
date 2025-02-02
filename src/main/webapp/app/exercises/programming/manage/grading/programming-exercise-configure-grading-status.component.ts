import { Component, Input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Two status indicators for the test case table:
 * - Are there unsaved changes?
 * - Have test cases been changed but the student submissions were not triggered?
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-status',
    template: `
        <div class="d-flex flex-column justify-content-between">
            @if (hasUnsavedTestCaseChanges || hasUnsavedCategoryChanges) {
                <div id="test-case-status-unsaved-changes" class="d-flex align-items-center badge bg-warning mb-1">
                    <fa-icon class="ms-2 text-white" [icon]="faExclamationTriangle" />
                    @if (hasUnsavedTestCaseChanges && hasUnsavedCategoryChanges) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedChanges"></span>
                    }
                    @if (hasUnsavedTestCaseChanges && !hasUnsavedCategoryChanges) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedTestCaseChanges"></span>
                    }
                    @if (!hasUnsavedTestCaseChanges && hasUnsavedCategoryChanges) {
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.unsavedCategoryChanges"></span>
                    }
                </div>
            } @else {
                <div id="test-case-status-no-unsaved-changes" class="d-flex align-items-center badge bg-success mb-1">
                    <fa-icon class="ms-2 text-white" [icon]="faCheckCircle" />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.noUnsavedChanges"></span>
                </div>
            }
            <ng-template #noUnsavedChanges>
                <div id="test-case-status-no-unsaved-changes" class="d-flex align-items-center badge bg-success mb-1">
                    <fa-icon class="ms-2 text-white" [icon]="faCheckCircle" />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.status.noUnsavedChanges"></span>
                </div>
            </ng-template>
            @if (exerciseIsReleasedAndHasResults) {
                @if (hasUpdatedGradingConfig) {
                    <div id="test-case-status-updated" class="d-flex align-items-center badge bg-warning">
                        <fa-icon
                            class="ms-2 text-white"
                            [icon]="faExclamationTriangle"
                            [ngbTooltip]="'artemisApp.programmingExercise.configureGrading.updatedGradingConfigTooltip' | artemisTranslate"
                        />
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.updatedGradingConfigShort"></span>
                    </div>
                } @else {
                    <div id="test-case-status-no-updated" class="d-flex align-items-center badge bg-success">
                        <fa-icon class="ms-2 text-white" [icon]="faCheckCircle" />
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.noUpdatedGradingConfig"></span>
                    </div>
                }
                <ng-template #noUpdatedGradingConfig>
                    <div id="test-case-status-no-updated" class="d-flex align-items-center badge bg-success">
                        <fa-icon class="ms-2 text-white" [icon]="faCheckCircle" />
                        <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.noUpdatedGradingConfig"></span>
                    </div>
                </ng-template>
            } @else {
                <div id="test-case-status-not-released" class="d-flex align-items-center badge bg-secondary">
                    <fa-icon
                        class="ms-2 text-white"
                        [icon]="faQuestionCircle"
                        [ngbTooltip]="'artemisApp.programmingExercise.configureGrading.notReleasedTooltip' | artemisTranslate"
                    />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.notReleased"></span>
                </div>
            }
            <ng-template #notReleased>
                <div id="test-case-status-not-released" class="d-flex align-items-center badge bg-secondary">
                    <fa-icon
                        class="ms-2 text-white"
                        [icon]="faQuestionCircle"
                        [ngbTooltip]="'artemisApp.programmingExercise.configureGrading.notReleasedTooltip' | artemisTranslate"
                    />
                    <span class="ms-1" jhiTranslate="artemisApp.programmingExercise.configureGrading.notReleased"></span>
                </div>
            </ng-template>
        </div>
    `,
    imports: [FaIconComponent, TranslateDirective, NgbTooltip, ArtemisTranslatePipe],
})
export class ProgrammingExerciseConfigureGradingStatusComponent {
    @Input() exerciseIsReleasedAndHasResults: boolean;
    @Input() hasUnsavedTestCaseChanges: boolean;
    @Input() hasUnsavedCategoryChanges: boolean;
    @Input() hasUpdatedGradingConfig: boolean;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faQuestionCircle = faQuestionCircle;
}
