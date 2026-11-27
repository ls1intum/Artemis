import { Component, input } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons/faBullhorn';
import { faHashtag } from '@fortawesome/free-solid-svg-icons/faHashtag';
import { faLock } from '@fortawesome/free-solid-svg-icons/faLock';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-channel-icon',
    templateUrl: './channel-icon.component.html',
    imports: [FaIconComponent],
})
export class ChannelIconComponent {
    isPublic = input<boolean>(true);
    isAnnouncementChannel = input<boolean>(false);

    // icons
    readonly faHashtag = faHashtag;
    readonly faLock = faLock;
    readonly faBullhorn = faBullhorn;

    getIcon() {
        if (this.isAnnouncementChannel()) {
            return this.faBullhorn;
        }
        return this.isPublic() ? this.faHashtag : this.faLock;
    }
}
