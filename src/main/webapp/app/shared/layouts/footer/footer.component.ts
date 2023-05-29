import { Component, OnInit } from '@angular/core';
import { VERSION } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html',
    styleUrls: ['./footer.scss'],
})
export class FooterComponent implements OnInit {
    readonly releaseNotesUrl = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;
    readonly requestChangeUrl = 'https://github.com/ls1intum/Artemis/issues/new/choose';

    email: string;
    gitBranch: string;
    gitCommitId: string;
    gitTimestamp: string;
    gitCommitUser: string;
    testServer: boolean;
    inProduction: boolean;

    constructor(private profileService: ProfileService) {}

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.contact = profileInfo.contact;
            this.gitBranch = profileInfo.git.branch;
            this.gitCommitId = profileInfo.git.commit.id.abbrev;
            this.gitTimestamp = new Date(profileInfo.git.commit.time).toUTCString();
            this.gitCommitUser = profileInfo.git.commit.user.name;
            this.testServer = profileInfo.testServer ?? false;
            this.inProduction = profileInfo.inProduction;
        });
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
