import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Exercise, requiresChannelName } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-title-channel-name',
    templateUrl: './exercise-title-channel-name.component.html',
})
export class ExerciseTitleChannelNameComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() titlePattern: string;
    @Input() minTitleLength: number;
    @Input() isExamMode: boolean;
    @Input() isImport: boolean;
    @Input() hideTitleLabel: boolean;

    @Output() onTitleChange = new EventEmitter<string>();
    @Output() onChannelNameChange = new EventEmitter<string>();

    hideChannelNameInput = false;
    ngOnInit() {
        this.hideChannelNameInput = !requiresChannelName(this.exercise, this.isExamMode, this.isImport);
    }

    updateTitle(newTitle: string) {
        this.exercise.title = newTitle;
        this.onTitleChange.emit(newTitle);
    }

    updateChannelName(newName: string) {
        this.exercise.channelName = newName;
        this.onChannelNameChange.emit(newName);
    }
}
