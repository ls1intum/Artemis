import { Component, input } from '@angular/core';
import { faBoxArchive, faBullhorn, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-channel-icon',
    templateUrl: './channel-icon.component.html',
    imports: [FaIconComponent],
})
export class ChannelIconComponent {
    isPublic = input<boolean>(true);
    isArchived = input<boolean>(false);
    isAnnouncementChannel = input<boolean>(false);

    // icons
    readonly faHashtag = faHashtag;
    readonly faLock = faLock;
    readonly faBoxArchive = faBoxArchive;
    readonly faBullhorn = faBullhorn;
}
