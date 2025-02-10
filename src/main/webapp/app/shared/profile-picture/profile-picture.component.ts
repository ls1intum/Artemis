import { Component, OnChanges, OnInit, input } from '@angular/core';
import { faCog } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { getInitialsFromString } from 'app/utils/text.utils';
import { getBackgroundColorHue } from 'app/utils/color.utils';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-profile-picture',
    templateUrl: './profile-picture.component.html',
    styleUrls: ['./profile-picture.component.scss'],
    imports: [ArtemisSharedCommonModule, RouterLink],
})
export class ProfilePictureComponent implements OnInit, OnChanges {
    readonly imageSizeInRem = input<string>('2.15');
    readonly fontSizeInRem = input<string>('0.9');
    authorName = input<string | undefined>(undefined);
    authorId = input<number | undefined>(undefined);
    imageUrl = input<string | undefined>(undefined);
    readonly imageClass = input<string>('');
    readonly defaultPictureClass = input<string>('');
    readonly imageId = input<string>('');
    readonly defaultPictureId = input<string>('');
    readonly isEditable = input<boolean>(false);
    readonly isGray = input<boolean>(false);

    profilePictureBackgroundColor: string;
    userProfilePictureInitials: string;
    imageSize: string;
    fontSize: string;

    // Icons
    protected readonly faCog = faCog;

    ngOnInit(): void {
        this.updateImageData();
    }

    ngOnChanges() {
        this.updateImageData();
    }

    private updateImageData() {
        this.userProfilePictureInitials = this.authorName() === undefined ? 'NA' : getInitialsFromString(this.authorName()!);
        this.profilePictureBackgroundColor = getBackgroundColorHue(this.authorId()?.toString());
        this.imageSize = this.imageSizeInRem().replaceAll('rem', '') + 'rem';
        this.fontSize = this.fontSizeInRem().replaceAll('rem', '') + 'rem';
    }
}
