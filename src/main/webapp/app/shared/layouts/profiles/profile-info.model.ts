import { ActiveFeatureToggles } from 'app/shared/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { ProgrammingLanguageFeature } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';
import { Saml2Config } from 'app/home/saml2-login/saml2.config';

export class ProfileInfo {
    public activeProfiles: string[];
    public ribbonEnv: string;
    public inProduction: boolean;
    public openApiEnabled?: boolean;
    public sentry?: { dsn: string };
    public features: ActiveFeatureToggles;
    public guidedTourMapping?: GuidedTourMapping;
    public buildPlanURLTemplate: string;
    public commitHashURLTemplate: string;
    public sshCloneURLTemplate: string;
    public sshKeysURL: string;
    public externalUserManagementURL: string;
    public externalUserManagementName: string;
    public contact: string;
    public testServer?: boolean;
    public allowedMinimumOrionVersion: string;
    public registrationEnabled?: boolean;
    public needsToAcceptTerms?: boolean;
    public allowedEmailPattern?: string;
    public allowedEmailPatternReadable?: string;
    public allowedLdapUsernamePattern?: string;
    public allowedCourseRegistrationUsernamePattern?: string;
    public accountName?: string;
    public versionControlUrl?: string;
    public versionControlAccessToken?: boolean;
    public programmingLanguageFeatures: ProgrammingLanguageFeature[];
    public saml2?: Saml2Config;
    public textAssessmentAnalyticsEnabled?: boolean;
    public studentExamStoreSessionData?: boolean;
    public useExternal: boolean;
    public externalCredentialProvider: string;
    public externalPasswordResetLinkMap: Map<string, string>;
    public git: {
        branch: string;
        commit: {
            id: {
                abbrev: string;
            };
        };
    };
}
