import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { VERSION } from 'app/app.constants';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { ContributorModel } from 'app/core/about-us/models/contributor-model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-about-us',
    templateUrl: './about-us.component.html',
    styleUrls: ['./about-us.component.scss'],
    imports: [TranslateDirective, ArtemisTranslatePipe, RouterLink],
})
export class AboutUsComponent implements OnInit {
    private profileService = inject(ProfileService);
    private staticContentService = inject(StaticContentService);

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
        ['exercises.programming', { programmingUrl: 'https://docs.artemis.tum.de/instructor/exercises/programming-exercise' }],
        ['exercises.quiz', { quizUrl: 'https://docs.artemis.tum.de/instructor/exercises/quiz-exercise' }],
        ['exercises.modeling', { modelingUrl: 'https://docs.artemis.tum.de/instructor/exercises/modeling-exercise', apollonUrl: 'https://apollon.ase.in.tum.de/' }],
        ['exercises.text', { textUrl: 'https://docs.artemis.tum.de/instructor/exercises/text-exercise', athenaUrl: 'https://github.com/ls1intum/edutelligence/tree/main/athena' }],
        ['exercises.fileUpload', { fileUploadUrl: 'https://docs.artemis.tum.de/instructor/exercises/file-upload-exercise' }],
        ['exam', { examModeUrl: 'https://docs.artemis.tum.de/instructor/exams/intro', studentFeatureUrl: '/features/students', instructorFeatureUrl: '/features/instructors' }],
        ['grading', { gradingUrl: 'https://docs.artemis.tum.de/instructor/grading' }],
        ['assessment', { assessmentUrl: 'https://docs.artemis.tum.de/instructor/assessment' }],
        ['communication', { communicationUrl: 'https://docs.artemis.tum.de/student/communication' }],
        ['notifications', { notificationsURL: 'https://docs.artemis.tum.de/student/notifications' }],
        ['teamExercises', { teamExercisesUrl: 'https://docs.artemis.tum.de/instructor/exercises/team-exercise' }],
        ['lectures', { lecturesUrl: 'https://docs.artemis.tum.de/instructor/lectures' }],
        ['integratedMarkdownEditor', { markdownEditorUrl: 'https://docs.artemis.tum.de/student/markdown-support' }],
        ['plagiarismChecks', { jPlagUrl: 'https://github.com/jplag/JPlag/', plagiarismChecksUrl: 'https://docs.artemis.tum.de/instructor/plagiarism-check' }],
        ['learningAnalytics', { learningAnalyticsUrl: 'https://docs.artemis.tum.de/instructor/learning-analytics' }],
        ['adaptiveLearning', { adaptiveLearningUrl: 'https://docs.artemis.tum.de/instructor/adaptive-learning' }],
        ['tutorialGroups', { tutorialGroupsUrl: 'https://docs.artemis.tum.de/instructor/tutorial-groups' }],
        ['iris', { irisUrl: 'https://artemis.tum.de/about-iris' }],
        ['scalable', { scalingUrl: 'https://docs.artemis.tum.de/admin/scaling' }],
        ['highUserSatisfaction', { userExperienceUrl: 'https://docs.artemis.tum.de/student/user-experience' }],
        ['customizable', { customizableUrl: 'https://docs.artemis.tum.de/instructor/courses' }],
        ['openSource', { openSourceUrl: 'https://docs.artemis.tum.de/developer/open-source' }],
    ];

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

        const profileInfo = this.profileService.getProfileInfo();
        this.contact = profileInfo.contact;
        if (profileInfo.git) {
            this.gitCommitId = profileInfo.git.commit.id.abbrev;
            this.gitBranchName = profileInfo.git.branch;
        }
        this.operatorName = profileInfo.operatorName;
        this.operatorAdminName = profileInfo.operatorAdminName;
        this.operatorContactEmail = profileInfo.contact;
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
