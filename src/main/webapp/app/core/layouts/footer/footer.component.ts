import { Component, OnInit, inject, signal } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html',
    styleUrls: ['./footer.scss'],
    imports: [TranslateDirective, RouterLink, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class FooterComponent implements OnInit {
    private profileService = inject(ProfileService);

    readonly RELEASE_URL = 'https://github.com/ls1intum/Artemis/releases';
    readonly FEEDBACK_URL = 'https://github.com/ls1intum/Artemis/issues/new/choose';

    email: string;
    readonly gitBranch = signal<string>(undefined!);
    readonly gitCommitId = signal<string>(undefined!);
    readonly gitTimestamp = signal<dayjs.Dayjs>(undefined!);
    readonly gitCommitUser = signal<string>(undefined!);
    readonly isTestServer = signal<boolean>(undefined!);
    readonly isProduction = signal<boolean>(undefined!);

    ngOnInit(): void {
        const profileInfo = this.profileService.getProfileInfo();
        this.contact = profileInfo.contact;
        this.gitBranch.set(profileInfo.git.branch);
        this.gitCommitId.set(profileInfo.git.commit.id.abbrev);
        this.gitTimestamp.set(dayjs(profileInfo.git.commit.time));
        this.gitCommitUser.set(profileInfo.git.commit.user.name);
        this.isTestServer.set(this.profileService.isTestServer());
        this.isProduction.set(this.profileService.isProduction());
    }

    set contact(mail: string) {
        this.email =
            'mailto:' +
            mail +
            '?body=Note%3A%20Please%20send%20only%20support%2Ffeature' +
            '%20request%20or%20bug%20reports%20regarding%20the%20Artemis' +
            '%20Platform%20to%20this%20address.%20Please%20check' +
            '%20our%20public%20bug%20tracker%20at%20https%3A%2F%2Fgithub.com' +
            '%2Fls1intum%2FArtemis%20for%20known%20bugs.%0AFor%20questions' +
            '%20regarding%20exercises%20and%20their%20content%2C%20please%20contact%20your%20instructors.';
    }
}
