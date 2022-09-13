import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-exercise-mode-switch',
    templateUrl: './exercise-mode-switch.component.html',
    providers: [],
})
export class ExerciseModeSwitchComponent {
    @Input() readonly exerciseID: String;
    @Input() practiceModeAvailable = true;
    @Input() practiceMode = false;

    @Output() readonly onTogglePracticeMode: EventEmitter<boolean> = new EventEmitter();

    constructor() {}

    public togglePracticeMode(toggle: boolean): void {
        if (this.practiceModeAvailable) {
            this.onTogglePracticeMode.emit(toggle);
        }
    }
}
