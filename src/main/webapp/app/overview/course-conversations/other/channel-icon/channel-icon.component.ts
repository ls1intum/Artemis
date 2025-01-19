import { Component, Input } from '@angular/core';
import { faBoxArchive, faBullhorn, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-channel-icon',
    templateUrl: './channel-icon.component.html',
    imports: [FaIconComponent],
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
    faBullhorn = faBullhorn;
}
