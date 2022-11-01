import { Component, Input } from '@angular/core';
import { faLock } from '@fortawesome/free-solid-svg-icons';
import { faHashtag } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-channel-icon',
    templateUrl: './channel-icon.component.html',
    styleUrls: ['./channel-icon.component.scss'],
})
export class ChannelIconComponent {
    @Input()
    isPublic = true;

    // icons
    faHashtag = faHashtag;
    faLock = faLock;
}
