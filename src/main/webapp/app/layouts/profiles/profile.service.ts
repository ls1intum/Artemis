import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';

import { SERVER_API_URL } from 'app/app.constants';
import { ProfileInfo } from './profile-info.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private infoUrl = SERVER_API_URL + 'management/info';
    private profileInfo: Promise<ProfileInfo>;

    constructor(private http: HttpClient) {}

    getProfileInfo(): Promise<ProfileInfo> {
        if (!this.profileInfo) {
            this.profileInfo = this.http
                .get<ProfileInfo>(this.infoUrl, { observe: 'response' })
                .pipe(
                    map((res: HttpResponse<ProfileInfo>) => {
                        const data = res.body;
                        const profileInfo = new ProfileInfo();
                        profileInfo.activeProfiles = data.activeProfiles;
                        const displayRibbonOnProfiles = data['display-ribbon-on-profiles'].split(',');
                        if (profileInfo.activeProfiles) {
                            const ribbonProfiles = displayRibbonOnProfiles.filter((profile: string) =>
                                profileInfo.activeProfiles.includes(profile)
                            );
                            if (ribbonProfiles.length !== 0) {
                                profileInfo.ribbonEnv = ribbonProfiles[0];
                            }
                            profileInfo.inProduction = profileInfo.activeProfiles.includes('prod');
                        }
                        return profileInfo;
                    })
                )
                .toPromise();
        }
        return this.profileInfo;
    }
}
