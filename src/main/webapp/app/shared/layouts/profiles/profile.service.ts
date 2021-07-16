import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';

import { SERVER_API_URL } from 'app/app.constants';
import { ProfileInfo } from './profile-info.model';
import { BehaviorSubject, Observable, OperatorFunction } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import * as _ from 'lodash';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Saml2Config } from 'app/home/saml2-login/saml2.config';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private infoUrl = SERVER_API_URL + 'management/info';
    private profileInfo: BehaviorSubject<ProfileInfo | undefined>;

    constructor(private http: HttpClient, private featureToggleService: FeatureToggleService) {}

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
                        const displayRibbonOnProfiles = data['display-ribbon-on-profiles'].split(',');

                        this.mapGuidedTourConfig(data, profileInfo);
                        this.mapAllowedOrionVersions(data, profileInfo);
                        this.mapTestServer(data, profileInfo);
                        ProfileService.mapSaml2Config(data, profileInfo);

                        if (profileInfo.activeProfiles) {
                            const ribbonProfiles = displayRibbonOnProfiles.filter((profile: string) => profileInfo.activeProfiles.includes(profile));
                            if (ribbonProfiles.length !== 0) {
                                profileInfo.ribbonEnv = ribbonProfiles[0];
                            }
                            profileInfo.inProduction = profileInfo.activeProfiles.includes('prod');
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
                        profileInfo.registrationEnabled = data.registrationEnabled;
                        profileInfo.needsToAcceptTerms = data.needsToAcceptTerms;
                        profileInfo.allowedEmailPattern = data.allowedEmailPattern;
                        profileInfo.allowedEmailPatternReadable = data.allowedEmailPatternReadable;
                        profileInfo.allowedLdapUsernamePattern = data.allowedLdapUsernamePattern;
                        profileInfo.allowedCourseRegistrationUsernamePattern = data.allowedCourseRegistrationUsernamePattern;
                        profileInfo.accountName = data.accountName;
                        profileInfo.versionControlUrl = data.versionControlUrl;
                        profileInfo.programmingLanguageFeatures = data.programmingLanguageFeatures;

                        return profileInfo;
                    }),
                )
                .subscribe((profileInfo: ProfileInfo) => {
                    this.profileInfo.next(profileInfo);
                    this.featureToggleService.initializeFeatureToggles(profileInfo.features);
                });
        }

        return this.profileInfo.pipe(filter((x) => x != undefined) as OperatorFunction<ProfileInfo | undefined, ProfileInfo>);
    }

    private mapAllowedOrionVersions(data: any, profileInfo: ProfileInfo) {
        profileInfo.allowedMinimumOrionVersion = data['allowed-minimum-orion-version'];
    }

    private mapTestServer(data: any, profileInfo: ProfileInfo) {
        profileInfo.testServer = data['test-server'];
    }

    private mapGuidedTourConfig(data: any, profileInfo: ProfileInfo) {
        /** map guided tour configuration */
        const guidedTourMapping = data['guided-tour'];
        if (guidedTourMapping) {
            guidedTourMapping.tours = _.reduce(guidedTourMapping.tours, _.extend);
            profileInfo.guidedTourMapping = guidedTourMapping;
        }
    }

    private static mapSaml2Config(data: any, profileInfo: ProfileInfo) {
        if (data.saml2) {
            profileInfo.saml2 = new Saml2Config();
            profileInfo.saml2.buttonLabel = data.saml2['button-label'] || 'SAML2 Login';
            profileInfo.saml2.enablePassword = data.saml2['enable-password'] || false;
        }
    }
}
