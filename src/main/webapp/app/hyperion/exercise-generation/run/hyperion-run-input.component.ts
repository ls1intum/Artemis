import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TumUiPanelComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseGenerationInput } from 'app/openapi/model/exercise-generation-input';

/** The owner's original brief and selected feedback, unchanged by later review-thread edits. */
@Component({
    selector: 'jhi-hyperion-run-input',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiPanelComponent, ArtemisTranslatePipe, TranslateDirective],
    template: `
        <tum-ui-panel
            toggleable
            [collapsed]="false"
            [header]="(adapting() ? 'artemisApp.hyperion.generation.run.adaptationInput' : 'artemisApp.hyperion.generation.run.generationInput') | artemisTranslate"
            data-testid="hyperion-run-input"
        >
            @if (context().prompt; as prompt) {
                <p class="mt-0 mb-3 whitespace-pre-wrap wrap-anywhere" data-testid="hyperion-run-input-prompt">{{ prompt }}</p>
            }
            @if (context().reviewFeedback?.length) {
                <h2 class="text-base font-semibold" jhiTranslate="artemisApp.hyperion.generation.run.selectedFeedback"></h2>
                <ul class="m-0 flex list-none flex-col gap-3 p-0" data-testid="hyperion-run-input-feedback">
                    @for (thread of context().reviewFeedback; track $index) {
                        <li class="border-l-2 border-border pl-3">
                            <p class="mt-0 mb-1 text-sm text-muted-color">
                                @if (thread.targetType) {
                                    {{ 'artemisApp.hyperion.generation.run.feedbackLocation.' + thread.targetType | artemisTranslate }}
                                }
                                @if (thread.filePath) {
                                    · {{ thread.filePath }}
                                }
                                @if (thread.lineNumber) {
                                    :{{ thread.lineNumber }}
                                }
                            </p>
                            @for (comment of thread.comments; track $index) {
                                <p class="mt-0 mb-2 whitespace-pre-wrap wrap-anywhere">{{ comment }}</p>
                            }
                        </li>
                    }
                </ul>
            }
        </tum-ui-panel>
    `,
})
export class HyperionRunInputComponent {
    readonly context = input.required<ExerciseGenerationInput>();
    readonly adapting = input(false);
}
