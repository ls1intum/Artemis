import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
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
 *
 * `liveUpdates` is the only mode input: the legacy child's `personalParticipation` is derived from it in the
 * template rather than accepted as a second, independently-bindable input. Two independent inputs would let a host
 * pass a mode and a personal flag that disagree, which is exactly the drift this component exists to remove.
 */
@Component({
    selector: 'jhi-problem-statement-renderer',
    templateUrl: './problem-statement-renderer.component.html',
    styleUrls: ['./problem-statement-renderer.component.scss'],
    imports: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionSsrComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProblemStatementRendererComponent {
    private featureToggleService = inject(FeatureToggleService);

    // Optional, mirroring both renderers: several hosts bind a class field that is undefined until the exercise has
    // loaded, and `input.required` would not catch that anyway (it only checks that the binding is present).
    readonly exercise = input<ProgrammingExercise>();
    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly liveUpdates = input<SsrLiveUpdates>('none');

    readonly onNoInstructionsAvailable = output<void>();

    // getFeatureToggleActive already returns Observable<boolean> for a single feature.
    // What actually prevents a flash of the SSR renderer on a fresh page load is the blocking APP_INITIALIZER in
    // app.config.ts, which awaits ProfileService.loadProfileInfo() (and, inside it, initializeFeatureToggles(...)
    // with the real server-provided feature list) before Angular bootstraps any component. By the time this
    // component's constructor subscribes below, the underlying BehaviorSubject already holds the real toggle state,
    // not defaultActiveFeatureState's "every feature active" default, so this Observable's first (synchronous)
    // emission is already correct, and `initialValue: false` below is never actually observed in production.
    // It stays as a defensive fail-closed default (prefer the legacy renderer) in case that guarantee ever breaks,
    // and it keeps `ssrEnabled` typed as `boolean` instead of `boolean | undefined`.
    readonly ssrEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.SsrProblemStatement), { initialValue: false });
}
