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
    @Input() hideChannelName?: boolean;
    @Input() minTitleLength: number;
    @Input() initChannelName = true;

    @Output() titleChange = new EventEmitter<string>();
    @Output() channelNameChange = new EventEmitter<string>();

    ngOnInit(): void {
        if (!this.channelNamePrefix) {
            this.channelNamePrefix = '';
        }

        if (this.initChannelName) {
            // Defer updating the channel name into the next change detection cycle to avoid the
            // "NG0100: Expression has changed after it was checked" error
            setTimeout(() => {
                this.formatChannelName(this.channelNamePrefix + (this.title ?? ''));
            });
        }
    }

    updateTitle(newTitle: string) {
        this.title = newTitle;
        this.titleChange.emit(this.title);
        this.formatChannelName(this.channelNamePrefix + this.title);
    }

    formatChannelName(newName: string, allowDuplicateDashes = false) {
        newName = newName.toLowerCase().slice(0, 30);
        const regex = allowDuplicateDashes ? /[^a-z0-9-]+/g : /[^a-z0-9]+/g;
        this.channelName = newName.replaceAll(regex, '-');
        this.channelNameChange.emit(this.channelName);
    }
}
