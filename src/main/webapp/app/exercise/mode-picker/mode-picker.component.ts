import { TumAetUiButtonDirective, TumAetUiButtonSeverity } from '@tumaet/ui-angular';

import { Component, input, output } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type ModePickerOption<TMode> = {
    value: TMode;
    labelKey?: string;
    label?: string;
    btnClass?: string;
    severity?: TumAetUiButtonSeverity;
};

@Component({
    selector: 'jhi-mode-picker',
    templateUrl: './mode-picker.component.html',
    imports: [TumAetUiButtonDirective, ArtemisTranslatePipe],
})
export class ModePickerComponent<TMode> {
    readonly options = input<ModePickerOption<TMode>[]>(undefined!);
    readonly disabled = input(false);

    readonly value = input<TMode>(undefined!);
    readonly valueChange = output<TMode>();

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param mode chosen mode of type {TMode}
     */
    setMode(mode: TMode) {
        if (!this.disabled() && mode !== this.value()) {
            this.valueChange.emit(mode);
        }
    }
}
