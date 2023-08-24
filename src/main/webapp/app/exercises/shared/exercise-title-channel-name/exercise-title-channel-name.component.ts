import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { Exercise, requiresChannelName } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-title-channel-name',
    templateUrl: './exercise-title-channel-name.component.html',
})
export class ExerciseTitleChannelNameComponent implements OnChanges {
    @Input() exercise: Exercise;
    @Input() course?: Course;
    @Input() titlePattern: string;
    @Input() minTitleLength: number;
    @Input() isExamMode: boolean;
    @Input() isImport: boolean;
    @Input() hideTitleLabel: boolean;

    @Output() onTitleChange = new EventEmitter<string>();
    @Output() onChannelNameChange = new EventEmitter<string>();

    hideChannelNameInput = false;
    ngOnChanges(changes: SimpleChanges) {
        if (changes.exercise || changes.course || changes.isExamMode || this.isImport) {
            this.hideChannelNameInput = !requiresChannelName(this.exercise, this.isExamMode, this.isImport);
        }
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
