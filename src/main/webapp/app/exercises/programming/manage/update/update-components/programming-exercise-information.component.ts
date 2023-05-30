import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { InfoStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInformationComponent {
    @Input() isImportFromExistingExercise: boolean;
    @Input() isExamMode: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() channelName: string | undefined;
    @Input() shouldHidePreview = false;
    @Input() infoInputs: InfoStepInputs;
    @Input() auxiliaryRepositoriesSupported = false;

    @Output() channelNameChange = new EventEmitter<string>();

    updateChannelName(newChannelName: string) {
        this.channelName = newChannelName;
        this.channelNameChange.emit(newChannelName);
    }
}
