import { Component, OnChanges, OnInit, input } from '@angular/core';
import { faCog } from '@fortawesome/free-solid-svg-icons';
import { getInitialsFromString } from 'app/shared/util/text.utils';
import { getBackgroundColorHue } from 'app/shared/util/color.utils';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-profile-picture',
    templateUrl: './profile-picture.component.html',
    styleUrls: ['./profile-picture.component.scss'],
    imports: [RouterLink, CommonModule, FontAwesomeModule],
})
export class ProfilePictureComponent implements OnInit, OnChanges {
    imageSizeInRem = input<string>('2.15');
    fontSizeInRem = input<string>('0.9');
    authorName = input<string | undefined>(undefined);
    authorId = input<number | undefined>(undefined);
    imageUrl = input<string | undefined>(undefined);
    imageClass = input<string>('');
    defaultPictureClass = input<string>('');
    imageId = input<string>('');
    defaultPictureId = input<string>('');
    isEditable = input<boolean>(false);
    isGray = input<boolean>(false);
    isBoxShadow = input<boolean>(false);

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
        this.profilePictureBackgroundColor = getBackgroundColorHue(this.authorId()?.toString() ?? this.authorName());
        this.imageSize = this.imageSizeInRem().replaceAll('rem', '') + 'rem';
        this.fontSize = this.fontSizeInRem().replaceAll('rem', '') + 'rem';
    }
}
