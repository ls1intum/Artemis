import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Component({
    selector: 'jhi-title-channel-name',
    templateUrl: './title-channel-name.component.html',
    styleUrls: ['./title-channel-name.component.scss'],
})
export class TitleChannelNameComponent implements OnInit {
    @Input() title: string | undefined;
    @Input() channelName: string | undefined;
    @Input() channelNamePrefix: string;
    @Input() pattern: string;
    @Input() isProgrammingExercise: boolean;

    @Input() minTitleLength: number;

    @Output() titleChange = new EventEmitter<string>();
    @Output() channelNameChange = new EventEmitter<string>();

    ngOnInit(): void {
        if (!this.channelNamePrefix) {
            this.channelNamePrefix = '';
        }
        if (this.channelName === '') {
            this.channelName = this.channelNamePrefix;
        }
    }

    updateTitle(newTitle: string) {
        this.title = newTitle;
        this.titleChange.emit(this.title);
        if (this.channelName !== undefined) {
            this.formatChannelName(this.channelNamePrefix + this.title);
        }
    }

    formatChannelName(newName: string) {
        this.channelName = newName.toLowerCase().slice(0, 30).replaceAll(' ', '-');
        this.channelNameChange.emit(this.channelName);
    }
}
