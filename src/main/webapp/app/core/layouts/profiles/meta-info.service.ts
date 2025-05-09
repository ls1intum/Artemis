import { Injectable, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

@Injectable({ providedIn: 'root' })
export class MetaInfoService {
    readonly features: string[];
    private meta = inject(Meta);

    public getMetaInformationFromHtml(): ProfileInfo {
        const activeProfilesMetaTag = this.meta.getTag("name='active-features'");
        const activeProfiles = activeProfilesMetaTag?.content.split(',') ?? [];
        return { activeProfiles } as ProfileInfo;
    }
}
