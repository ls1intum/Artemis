import { Component, Input } from '@angular/core';
import { faBell, faBoxArchive, faLock } from '@fortawesome/free-solid-svg-icons';
import { faHashtag } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-channel-icon',
    templateUrl: './channel-icon.component.html',
})
export class ChannelIconComponent {
    @Input()
    isPublic = true;

    @Input()
    isArchived = false;

    @Input()
    isAnnouncementChannel = false;
    // icons
    faHashtag = faHashtag;
    faLock = faLock;
    faBoxArchive = faBoxArchive;
    faBell = faBell;
}
