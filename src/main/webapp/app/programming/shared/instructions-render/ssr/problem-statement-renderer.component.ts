import { Component, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionSsrComponent, SsrLiveUpdates } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';

/**
 * Renders a programming exercise problem statement, choosing between the server-side renderer and the legacy
 * client-side pipeline based on the SsrProblemStatement feature toggle. The toggle acts as a kill switch: while it
 * is off, behavior is byte-for-byte the previous one.
 *
 * All six read-only hosts (course overview, code editor, repository view, assessment instructions, assessment
 * dashboard, detail overview list) bind this single component instead of picking a renderer themselves.
 */
@Component({
    selector: 'jhi-problem-statement-renderer',
    templateUrl: './problem-statement-renderer.component.html',
    styleUrls: ['./problem-statement-renderer.component.scss'],
    imports: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionSsrComponent],
})
export class ProblemStatementRendererComponent {
    private featureToggleService = inject(FeatureToggleService);

    readonly exercise = input.required<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly liveUpdates = input<SsrLiveUpdates>('none');
    readonly personalParticipation = input(false);

    readonly onNoInstructionsAvailable = output<void>();

    // getFeatureToggleActive already returns Observable<boolean> for a single feature.
    // Starts false so a page load never flashes the SSR renderer before the server-provided toggle state has arrived.
    readonly ssrEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.SsrProblemStatement), { initialValue: false });
}
