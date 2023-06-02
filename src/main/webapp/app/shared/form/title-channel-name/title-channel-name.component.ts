import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ControlContainer, NgForm } from '@angular/forms';

@Component({
    selector: 'jhi-title-channel-name',
    templateUrl: './title-channel-name.component.html',
    styleUrls: ['./title-channel-name.component.scss'],
    viewProviders: [{ provide: ControlContainer, useExisting: NgForm }],
})
export class TitleChannelNameComponent implements OnInit {
    @Input() title?: string;
    @Input() channelName?: string;
    @Input() channelNamePrefix: string;
    @Input() pattern: string;
    @Input() hideTitleLabel: boolean;
    @Input() emphasizeLabels = false;

    @Input() forceChannelName: boolean;

    @Input() minTitleLength: number;
    @Input() isTestExam?: boolean;

    @Output() titleChange = new EventEmitter<string>();
    @Output() channelNameChange = new EventEmitter<string>();

    ngOnInit(): void {
        if (!this.channelNamePrefix) {
            this.channelNamePrefix = '';
        }
        if ((this.channelName === '' || (this.forceChannelName && this.channelName === undefined)) && !this.isTestExam) {
            this.updateTitle(this.title || '');
        }
    }

    updateTitle(newTitle: string) {
        this.title = newTitle;
        this.titleChange.emit(this.title);
        if (this.channelName !== undefined || this.forceChannelName) {
            this.formatChannelName(this.channelNamePrefix + this.title);
        }
    }

    formatChannelName(newName: string) {
        if (!this.isTestExam) {
            this.channelName = newName.toLowerCase().slice(0, 30).replaceAll(' ', '-');
            this.channelNameChange.emit(this.channelName);
        }
    }
}
