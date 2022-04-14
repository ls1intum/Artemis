import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ModePickerOption<TMode> = {
    value: TMode;
    labelKey: string;
    btnClass: string;
};

@Component({
    selector: 'jhi-mode-picker',
    templateUrl: './mode-picker.component.html',
    styles: ['.btn.disabled { pointer-events: none }', '.btn-group.disabled { cursor: not-allowed; }'],
})
export class ModePickerComponent<TMode> {
    @Input() options: ModePickerOption<TMode>[];
    @Input() disabled = false;

    @Input() value: TMode;
    @Output() valueChange = new EventEmitter<TMode>();

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param mode chosen mode of type {TMode}
     */
    setMode(mode: TMode) {
        if (!this.disabled && mode !== this.value) {
            this.valueChange.emit(mode);
        }
    }
}
