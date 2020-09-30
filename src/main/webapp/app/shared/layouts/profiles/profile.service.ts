import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';

import { SERVER_API_URL } from 'app/app.constants';
import { ProfileInfo } from './profile-info.model';
import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import * as _ from 'lodash';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private infoUrl = SERVER_API_URL + 'management/info';
    private profileInfo: BehaviorSubject<ProfileInfo | null>;

    constructor(private http: HttpClient, private featureToggleService: FeatureToggleService) {}

    getProfileInfo(): BehaviorSubject<ProfileInfo | null> {
        if (!this.profileInfo) {
            this.profileInfo = new BehaviorSubject(null);
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

                        if (profileInfo.activeProfiles) {
                            const ribbonProfiles = displayRibbonOnProfiles.filter((profile: string) => profileInfo.activeProfiles.includes(profile));
                            if (ribbonProfiles.length !== 0) {
                                profileInfo.ribbonEnv = ribbonProfiles[0];
                            }
                            profileInfo.inProduction = profileInfo.activeProfiles.includes('prod');
                        }
                        profileInfo.sentry = data.sentry;
                        profileInfo.features = data.features;
                        profileInfo.buildPlanURLTemplate = data.buildPlanURLTemplate;
                        profileInfo.sshCloneURLTemplate = data.sshCloneURLTemplate;
                        profileInfo.sshKeysURL = data.sshKeysURL;
                        profileInfo.externalUserManagementName = data.externalUserManagementName;
                        profileInfo.externalUserManagementURL = data.externalUserManagementURL;
                        profileInfo.imprint = data.imprint;
                        profileInfo.contact = data.contact;
                        profileInfo.registrationEnabled = data.registrationEnabled;
                        profileInfo.allowedEmailPattern = data.allowedEmailPattern;

                        return profileInfo;
                    }),
                )
                .subscribe((profileInfo: ProfileInfo) => {
                    this.profileInfo.next(profileInfo);
                    this.featureToggleService.initializeFeatureToggles(profileInfo.features);
                });
        }

        return this.profileInfo;
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
}
