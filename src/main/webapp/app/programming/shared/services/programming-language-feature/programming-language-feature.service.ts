import { Injectable, inject } from '@angular/core';
import { ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Injectable({ providedIn: 'root' })
export class ProgrammingLanguageFeatureService {
    private profileService = inject(ProfileService);

    private programmingLanguageFeatures: Map<ProgrammingLanguage, ProgrammingLanguageFeature> = new Map<ProgrammingLanguage, ProgrammingLanguageFeature>();

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        profileInfo.programmingLanguageFeatures.forEach((programmingLanguageFeature) => {
            this.programmingLanguageFeatures.set(programmingLanguageFeature.programmingLanguage, programmingLanguageFeature);
        });
    }

    public getProgrammingLanguageFeature(programmingLanguage: ProgrammingLanguage): ProgrammingLanguageFeature | undefined {
        return this.programmingLanguageFeatures.get(programmingLanguage);
    }

    public supportsProgrammingLanguage(programmingLanguage: ProgrammingLanguage): boolean {
        // A programming language is supported if the server provided us with information about that language
        return this.programmingLanguageFeatures.has(programmingLanguage);
    }
}
