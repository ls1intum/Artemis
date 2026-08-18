import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionSsrComponent, SsrLiveUpdates } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';

/**
 * Renders a programming exercise problem statement, choosing between the server-side renderer and the legacy
 * client-side pipeline based on the SsrProblemStatement feature toggle. Turning the toggle off returns every host to
 * the legacy renderer, with one deliberate exception: which result websocket topic that renderer subscribes to on
 * staff-facing views. `personalParticipation` is derived from `liveUpdates` in the template (see below) rather than
 * passed per host, so the detail overview list and the repository view (template / solution repositories, and a tutor
 * looking at a student's repository) now subscribe to the exercise-wide topic instead of the viewer's personal one.
 * Both previously passed `true`, and a personal topic can never carry results for a template, solution or another
 * student's participation, so that subscription could not deliver the live updates it promised. Every other host ends
 * up with the value it passed before, or has no participation to subscribe for at all.
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

    /**
     * Exam exercises stay on the legacy renderer whatever the toggle says. The exclusion is central here rather than
     * per host because a host cannot see which renderer it ends up with: the student code editor, for one, hides its
     * instructions pane for course exercises and shows it only for exam ones, so its problem statement is an exam
     * problem statement whenever it is visible at all.
     *
     * The marker is `exerciseGroup`, the same positive test the code editor uses to decide that visibility, rather
     * than the absence of a course, which is also what an exercise whose course was not loaded looks like.
     */
    readonly serverRendered = computed(() => this.ssrEnabled() && this.exercise()?.exerciseGroup === undefined);
}
