import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ControlContainer, NgForm } from '@angular/forms';

@Component({
    selector: 'jhi-title-channel-name',
    templateUrl: './title-channel-name.component.html',
    viewProviders: [{ provide: ControlContainer, useExisting: NgForm }],
})
export class TitleChannelNameComponent implements OnInit {
    @Input() title?: string;
    @Input() channelName?: string;
    @Input() channelNamePrefix: string;
    @Input() titlePattern: string;
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
                // Remove trailing hyphens if title is not undefined or empty
                this.formatChannelName(this.channelNamePrefix + (this.title ?? ''), false, !!this.title);
            });
        }
    }

    updateTitle(newTitle: string) {
        this.title = newTitle;
        this.titleChange.emit(this.title);
        // Remove trailing hyphens if title is not undefined or empty
        this.formatChannelName(this.channelNamePrefix + this.title, false, !!this.title);
    }

    formatChannelName(newName: string, allowDuplicateHyphens = true, removeTrailingHyphens = false) {
        const specialCharacters = allowDuplicateHyphens ? /[^a-z0-9-]+/g : /[^a-z0-9]+/g;
        const trailingHyphens = removeTrailingHyphens ? /-$/ : new RegExp('[]');
        this.channelName = newName.toLowerCase().replaceAll(specialCharacters, '-').replace(trailingHyphens, '').slice(0, 30);
        this.channelNameChange.emit(this.channelName);
    }
}
