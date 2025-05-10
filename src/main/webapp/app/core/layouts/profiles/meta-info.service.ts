import { Injectable, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

@Injectable({ providedIn: 'root' })
export class MetaInfoService {
    readonly features: string[];
    private meta = inject(Meta);

    public getActiveProfilesAndModuleFeaturesFromHtmlMeta(): ProfileInfo {
        const activeProfiles = this.getActiveProfilesFromHtmlMeta();
        const activeModuleFeatures = this.getActiveModuleFeaturesFromHtmlMeta();
        return { activeProfiles, activeModuleFeatures } as ProfileInfo;
    }
    private getActiveProfilesFromHtmlMeta(): string[] {
        const activeProfilesMetaTag = this.meta.getTag("name='active-profiles'");
        return activeProfilesMetaTag?.content.split(',') ?? [];
    }
    private getActiveModuleFeaturesFromHtmlMeta(): string[] {
        const activeModuleFeaturesMetaTag = this.meta.getTag("name='active-module-features'");
        return activeModuleFeaturesMetaTag?.content.split(',') ?? [];
    }
}
