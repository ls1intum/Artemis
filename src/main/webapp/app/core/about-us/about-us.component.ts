import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
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
    private readonly ISSUE_BASE_URL = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';
    readonly BUG_REPORT_URL = `${this.ISSUE_BASE_URL}&labels=bug&template=bug-report.yml`;
    readonly FEATURE_REQUEST_URL = `${this.ISSUE_BASE_URL}&labels=feature&template=feature-request.yml`;
    readonly RELEASE_NOTES_URL = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;

    email: string;
    data: AboutUsModel;
    gitCommitId?: string;
    gitBranchName?: string;
    operatorName?: string;
    operatorAdminName?: string;
    operatorContactEmail?: string;

    // Array of tuple containing translation keys and translation values
    readonly SECTIONS: [string, { [key: string]: string }][] = [
        ['exercises.programming', { programmingUrl: 'https://docs.artemis.cit.tum.de/user/exercises/programming/' }],
        ['exercises.quiz', { quizUrl: 'https://docs.artemis.cit.tum.de/user/exercises/quiz/' }],
        ['exercises.modeling', { modelingUrl: 'https://docs.artemis.cit.tum.de/user/exercises/modeling/', apollonUrl: 'https://apollon.ase.in.tum.de/' }],
        ['exercises.text', { textUrl: 'https://docs.artemis.cit.tum.de/user/exercises/textual/', athenaUrl: 'https://github.com/ls1intum/Athena' }],
        ['exercises.fileUpload', { fileUploadUrl: 'https://docs.artemis.cit.tum.de/user/exercises/file-upload/' }],
        ['exam', { examModeUrl: 'https://docs.artemis.cit.tum.de/user/exam_mode/', studentFeatureUrl: '/features/students', instructorFeatureUrl: '/features/instructors' }],
        ['grading', { gradingUrl: 'https://docs.artemis.cit.tum.de/user/grading/' }],
        ['assessment', { assessmentUrl: 'https://docs.artemis.cit.tum.de/user/exercises/assessment/' }],
        ['communication', { communicationUrl: 'https://docs.artemis.cit.tum.de/user/communication/' }],
        ['notifications', { notificationsURL: 'https://docs.artemis.cit.tum.de/user/notifications' }],
        ['teamExercises', { teamExercisesUrl: 'https://docs.artemis.cit.tum.de/user/exercises/team-exercises/' }],
        ['lectures', { lecturesUrl: 'https://docs.artemis.cit.tum.de/user/lectures/' }],
        ['integratedMarkdownEditor', { markdownEditorUrl: 'https://docs.artemis.cit.tum.de/user/markdown-support/' }],
        ['plagiarismChecks', { jPlagUrl: 'https://github.com/jplag/JPlag/', plagiarismChecksUrl: 'https://docs.artemis.cit.tum.de/user/plagiarism-check/' }],
        ['learningAnalytics', { learningAnalyticsUrl: 'https://docs.artemis.cit.tum.de/user/learning-analytics/' }],
        ['adaptiveLearning', { adaptiveLearningUrl: 'https://docs.artemis.cit.tum.de/user/adaptive-learning/' }],
        ['tutorialGroups', { tutorialGroupsUrl: 'https://docs.artemis.cit.tum.de/user/tutorialgroups/' }],
        ['iris', { irisUrl: 'https://artemis.cit.tum.de/about-iris' }],
        ['scalable', { scalingUrl: 'https://docs.artemis.cit.tum.de/user/scaling/' }],
        ['highUserSatisfaction', { userExperienceUrl: 'https://docs.artemis.cit.tum.de/user/user-experience/' }],
        ['customizable', { customizableUrl: 'https://docs.artemis.cit.tum.de/user/courses/customizable' }],
        ['openSource', { openSourceUrl: 'https://docs.artemis.cit.tum.de/dev/open-source/' }],
    ];

    constructor(
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private staticContentService: StaticContentService,
    ) {}

    /**
     * On init get the json file from the Artemis server and save it.
     * On init get the mail data needed for the contact
     */
    ngOnInit(): void {
        this.staticContentService.getStaticJsonFromArtemisServer('about-us.json').subscribe((data) => {
            // Map contributors into the model, as the returned data are just plain objects
            this.data = {
                ...data,
                contributors: data.contributors.map((con: any) => new ContributorModel(con.fullName, con.photoDirectory, con.sortBy, con.role, con.website)),
            };

            // Sort by last name
            // Either the last "word" in the name, or the dedicated sortBy field, if present
            this.data?.contributors?.sort((a, b) => a.getSortIndex().localeCompare(b.getSortIndex()));
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.contact = profileInfo.contact;
            if (profileInfo.git) {
                this.gitCommitId = profileInfo.git.commit.id.abbrev;
                this.gitBranchName = profileInfo.git.branch;
            }
            this.operatorName = profileInfo.operatorName;
            this.operatorAdminName = profileInfo.operatorAdminName;
            this.operatorContactEmail = profileInfo.contact;
        });
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
