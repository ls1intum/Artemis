import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';

import { ProfileInfo } from '../profile-info.model';
import { BehaviorSubject, Observable, OperatorFunction } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { extend, reduce } from 'lodash-es';
import { Saml2Config } from 'app/core/home/saml2-login/saml2.config';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private http = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private infoUrl = 'management/info';
    private profileInfo: BehaviorSubject<ProfileInfo | undefined>;

    getProfileInfo(): Observable<ProfileInfo> {
        if (!this.profileInfo) {
            this.profileInfo = new BehaviorSubject(undefined);
            this.http
                .get<ProfileInfo>(this.infoUrl, { observe: 'response' })
                .pipe(
                    map((res: HttpResponse<ProfileInfo>) => {
                        const data = res.body!;
                        const profileInfo = new ProfileInfo();
                        profileInfo.activeProfiles = data.activeProfiles;
                        profileInfo.activeModuleFeatures = data.activeModuleFeatures;
                        const displayRibbonOnProfiles = data.ribbonEnv?.split(',') ?? [];

                        this.mapGuidedTourConfig(data, profileInfo);
                        this.mapTestServer(data, profileInfo);
                        ProfileService.mapSaml2Config(data, profileInfo);

                        if (profileInfo.activeProfiles) {
                            const ribbonProfiles = displayRibbonOnProfiles.filter((profile: string) => profileInfo.activeProfiles.includes(profile));
                            if (ribbonProfiles.length !== 0) {
                                profileInfo.ribbonEnv = ribbonProfiles[0];
                            }
                            profileInfo.inProduction = profileInfo.activeProfiles.includes('prod');
                            profileInfo.inDevelopment = profileInfo.activeProfiles.includes('dev');
                            profileInfo.openApiEnabled = profileInfo.activeProfiles.includes('openapi');
                        }
                        profileInfo.ribbonEnv = profileInfo.ribbonEnv ?? '';

                        profileInfo.sentry = data.sentry;
                        profileInfo.features = data.features;
                        profileInfo.buildPlanURLTemplate = data.buildPlanURLTemplate;
                        profileInfo.commitHashURLTemplate = data.commitHashURLTemplate;
                        profileInfo.sshCloneURLTemplate = data.sshCloneURLTemplate;
                        profileInfo.sshKeysURL = data.sshKeysURL;

                        // Both properties below are mandatory Strings (see profile-info.model.ts)
                        profileInfo.externalUserManagementName = data.externalUserManagementName ?? '';
                        profileInfo.externalUserManagementURL = data.externalUserManagementURL ?? '';

                        profileInfo.contact = data.contact;
                        profileInfo.operatorName = data.operatorName;
                        profileInfo.operatorAdminName = data.operatorAdminName;
                        profileInfo.registrationEnabled = data.registrationEnabled;
                        profileInfo.needsToAcceptTerms = data.needsToAcceptTerms;
                        profileInfo.allowedEmailPattern = data.allowedEmailPattern;
                        profileInfo.allowedEmailPatternReadable = data.allowedEmailPatternReadable;
                        profileInfo.allowedLdapUsernamePattern = data.allowedLdapUsernamePattern;
                        profileInfo.allowedCourseRegistrationUsernamePattern = data.allowedCourseRegistrationUsernamePattern;
                        profileInfo.accountName = data.accountName;
                        profileInfo.versionControlUrl = data.versionControlUrl;
                        profileInfo.versionControlName = data.versionControlName;
                        profileInfo.repositoryAuthenticationMechanisms = data.repositoryAuthenticationMechanisms;
                        profileInfo.continuousIntegrationName = data.continuousIntegrationName;
                        profileInfo.programmingLanguageFeatures = data.programmingLanguageFeatures;
                        profileInfo.textAssessmentAnalyticsEnabled = data.textAssessmentAnalyticsEnabled;
                        profileInfo.studentExamStoreSessionData = data.studentExamStoreSessionData;

                        profileInfo.useExternal = data.useExternal;
                        profileInfo.externalCredentialProvider = data.externalCredentialProvider;
                        profileInfo.externalPasswordResetLinkMap = data.externalPasswordResetLinkMap;

                        profileInfo.git = data.git;

                        profileInfo.theiaPortalURL = data.theiaPortalURL ?? '';

                        profileInfo.buildTimeoutMin = data.buildTimeoutMin;
                        profileInfo.buildTimeoutMax = data.buildTimeoutMax;
                        profileInfo.buildTimeoutDefault = data.buildTimeoutDefault;

                        profileInfo.defaultContainerCpuCount = data.defaultContainerCpuCount;
                        profileInfo.defaultContainerMemoryLimitInMB = data.defaultContainerMemoryLimitInMB;
                        profileInfo.defaultContainerMemorySwapLimitInMB = data.defaultContainerMemorySwapLimitInMB;

                        return profileInfo;
                    }),
                )
                .subscribe((profileInfo: ProfileInfo) => {
                    this.profileInfo.next(profileInfo);
                    this.featureToggleService.initializeFeatureToggles(profileInfo.features);
                    this.browserFingerprintService.initialize(profileInfo.studentExamStoreSessionData);
                });
        }

        return this.profileInfo.pipe(filter((info) => info != undefined) as OperatorFunction<ProfileInfo | undefined, ProfileInfo>);
    }

    private mapTestServer(data: any, profileInfo: ProfileInfo) {
        profileInfo.testServer = data['test-server'];
    }

    private mapGuidedTourConfig(data: any, profileInfo: ProfileInfo) {
        /** map guided tour configuration */
        const guidedTourMapping = data['guided-tour'];
        if (guidedTourMapping) {
            guidedTourMapping.tours = reduce(guidedTourMapping.tours, extend);
            profileInfo.guidedTourMapping = guidedTourMapping;
        }
    }

    private static mapSaml2Config(data: any, profileInfo: ProfileInfo) {
        if (data.saml2) {
            profileInfo.saml2 = new Saml2Config();
            profileInfo.saml2.identityProviderName = data.saml2['identity-provider-name'];
            profileInfo.saml2.buttonLabel = data.saml2['button-label'] || 'SAML2 Login';
            profileInfo.saml2.passwordLoginDisabled = data.saml2['password-login-disabled'] || false;
            profileInfo.saml2.enablePassword = data.saml2['enable-password'] || false;
        }
    }

    /**
     * Check if the given profile is active.
     * @param profile The profile to check.
     */
    public isProfileActive(profile: string): boolean {
        return this.profileInfo?.value?.activeProfiles?.includes(profile) ?? false;
    }

    /**
     * Check if the given module feature is active.
     * @param moduleFeatureName The module feature to check.
     */
    public isFeatureActive(moduleFeatureName: string): boolean {
        return this.profileInfo?.value.activeModuleFeatures?.includes(moduleFeatureName) ?? false;
    }
}
