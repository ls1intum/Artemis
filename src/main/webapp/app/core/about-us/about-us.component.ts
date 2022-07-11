import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { VERSION } from 'app/app.constants';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { ContributorModel } from 'app/core/about-us/models/contributor-model';

@Component({
    selector: 'jhi-about-us',
    templateUrl: './about-us.component.html',
    styleUrls: ['./about-us.component.scss'],
})
export class AboutUsComponent implements OnInit {
    private readonly issueBaseUrl = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';
    readonly bugReportUrl = `${this.issueBaseUrl}&labels=bug&template=bug-report.yml`;
    readonly featureRequestUrl = `${this.issueBaseUrl}&labels=feature&template=feature-request.yml`;
    readonly releaseNotesUrl = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;

    email: string;
    data: AboutUsModel;

    // Array of tuple containing translation keys and translation values
    readonly sections: [string, { [key: string]: string }][] = [
        ['exercises.programming', { programmingUrl: 'https://docs.artemis.ase.in.tum.de/user/exercises/programming/' }],
        ['exercises.quiz', { quizUrl: 'https://docs.artemis.ase.in.tum.de/user/exercises/quiz/' }],
        ['exercises.modeling', { modelingUrl: 'https://docs.artemis.ase.in.tum.de/user/exercises/modeling/', apollonUrl: 'https://apollon.ase.in.tum.de/' }],
        ['exercises.text', { textUrl: 'https://docs.artemis.ase.in.tum.de/user/exercises/textual/', athenaUrl: 'https://github.com/ls1intum/Athena' }],
        ['exercises.fileUpload', { fileUploadUrl: 'https://docs.artemis.ase.in.tum.de/user/exercises/file-upload/' }],
        ['exam', { studentFeatureUrl: '/features/students', instructorFeatureUrl: '/features/instructors' }],
        ['grading', {}],
        ['assessment', {}],
        ['communication', { communicationUrl: 'https://docs.artemis.ase.in.tum.de/user/communication/' }],
        ['notifications', {}],
        ['teamExercises', {}],
        ['lectures', {}],
        ['integratedMarkdownEditor', {}],
        ['plagiarismChecks', { jPlagUrl: 'https://github.com/jplag/JPlag' }],
        ['learningAnalytics', { learningAnalyticsUrl: 'https://docs.artemis.ase.in.tum.de/user/learning-analytics/' }],
        ['scalable', {}],
        ['highUserSatisfaction', {}],
        ['customizable', {}],
        ['openSource', {}],
    ];

    readonly SERVER_API_URL = SERVER_API_URL;

    constructor(private route: ActivatedRoute, private profileService: ProfileService, private staticContentService: StaticContentService) {}

    /**
     * On init get the json file from the Artemis server and save it.
     * On init get the mail data needed for the contact
     */
    ngOnInit(): void {
        this.staticContentService.getStaticJsonFromArtemisServer('about-us.json').subscribe((data) => {
            // Map contributors into the model, as the returned data are just plain objects
            this.data = { ...data, contributors: data.contributors.map((con: any) => new ContributorModel(con.fullName, con.photoDirectory, con.sortBy, con.role, con.website)) };

            // Sort by last name
            // Either the last "word" in the name, or the dedicated sortBy field, if present
            this.data?.contributors?.sort((a, b) => a.getSortIndex().localeCompare(b.getSortIndex()));
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
