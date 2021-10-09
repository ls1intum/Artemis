import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { VERSION } from 'app/app.constants';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';

@Component({
    selector: 'jhi-about-us',
    templateUrl: './about-us.component.html',
    styleUrls: ['./about-us.component.scss'],
})
export class AboutUsComponent implements OnInit {
    private readonly issueBaseUrl = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';
    readonly bugReportUrl = `${this.issueBaseUrl}&labels=bug&template=bug-report.yml`;
    readonly featureRequestUrl = `${this.issueBaseUrl}&labels=feature&template=feature-request.yml`;
    readonly examFeatureOverviewUrlStudents = '../features/students/';
    readonly examFeatureOverviewUrlInstructors = '../features/instructors/';
    readonly examInstructionsUrlStudents = 'https://docs.artemis.ase.in.tum.de/user/exams/students_guide/';
    readonly examInstructionsUrlInstructors = 'https://docs.artemis.ase.in.tum.de/user/exams/instructors_guide/';
    readonly releaseNotesUrl = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;

    email: string;
    data: AboutUsModel;

    readonly SERVER_API_URL = SERVER_API_URL;

    constructor(private route: ActivatedRoute, private profileService: ProfileService, private staticContentService: StaticContentService) {}

    /**
     * On init get the json file from the Artemis server and save it.
     * On init get the mail data needed for the contact
     */
    ngOnInit(): void {
        this.staticContentService.getStaticJsonFromArtemisServer('about-us.json').subscribe((data) => {
            this.data = data;
        });

        this.profileService
            .getProfileInfo()
            .pipe(
                filter(Boolean),
                tap((info: ProfileInfo) => {
                    this.contact = info.contact;
                }),
            )
            .subscribe();
    }

    /**
     * Create the mail reference for the contact
     */
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
