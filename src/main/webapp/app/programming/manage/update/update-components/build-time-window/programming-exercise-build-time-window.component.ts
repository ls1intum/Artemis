import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, signal } from '@angular/core';
import { TumAetUiDatePickerComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { BUILD_TIME_WINDOW_ERROR_TRANSLATION_KEYS, BuildTimeWindowError, normalizeBuildTimeWindow } from 'app/programming/shared/utils/build-time-window.utils';

/**
 * Edits the optional build time window of a programming exercise. Pushes of students outside the window are saved,
 * but not built. Either bound may stay empty. The exercise is edited in place, like in the other update components.
 *
 * The parent derives the validation error from the exercise dates (which this component does not observe) and passes it in.
 */
@Component({
    selector: 'jhi-programming-exercise-build-time-window',
    templateUrl: './programming-exercise-build-time-window.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, TumAetUiDatePickerComponent, TumAetUiMessageComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseBuildTimeWindowComponent {
    readonly programmingExercise = input.required<ProgrammingExercise>();
    /** The problem with the window that is derived from the exercise dates, undefined when the window is fine. */
    readonly error = input<BuildTimeWindowError | undefined>();

    protected readonly errorTranslationKeys = BUILD_TIME_WINDOW_ERROR_TRANSLATION_KEYS;

    protected readonly start = linkedSignal(() => normalizeBuildTimeWindow(this.programmingExercise().buildConfig?.advancedSettings?.buildTimeWindow)?.start);
    protected readonly end = linkedSignal(() => normalizeBuildTimeWindow(this.programmingExercise().buildConfig?.advancedSettings?.buildTimeWindow)?.end);

    /** The pickers keep their last valid value while the typed text is not a date yet, so that text is tracked separately. */
    protected readonly isStartTextValid = signal(true);
    protected readonly isEndTextValid = signal(true);

    /** Whether both pickers hold a date or nothing, as opposed to text that is not a date yet. */
    readonly isInputValid = computed(() => this.isStartTextValid() && this.isEndTextValid());

    protected onStartChange(start: dayjs.Dayjs | undefined): void {
        this.writeWindow(start, this.currentWindow()?.end);
    }

    protected onEndChange(end: dayjs.Dayjs | undefined): void {
        this.writeWindow(this.currentWindow()?.start, end);
    }

    /**
     * The window as it is stored on the exercise right now. The other bound is read from there and not from the pickers, because the exercise can be replaced
     * under us by the metadata synchronization with another editor, and writing back the stale value of the picker would undo their change.
     */
    private currentWindow() {
        return normalizeBuildTimeWindow(this.programmingExercise().buildConfig?.advancedSettings?.buildTimeWindow);
    }

    /** Stores the window on the build config. With both bounds empty the settings are removed, which means that there is no window. */
    private writeWindow(start: dayjs.Dayjs | undefined, end: dayjs.Dayjs | undefined): void {
        const buildConfig = this.programmingExercise().buildConfig;
        if (!buildConfig) {
            return;
        }
        this.start.set(start);
        this.end.set(end);
        buildConfig.advancedSettings = start || end ? { buildTimeWindow: { start, end } } : undefined;
    }
}
