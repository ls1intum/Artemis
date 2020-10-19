import { Injectable } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { filter, tap } from 'rxjs/operators';

/**
 * FeatureToggle, currently only supports PROGRAMMING_EXERCISES
 * @readonly
 * @enum {string}
 */
export type ProgrammingLanguageFeature = {
    programmingLanguage: ProgrammingLanguage;
    sequentialTestRuns: boolean;
    staticCodeAnalysis: boolean;
    bambooBuildSupported: boolean;
    jenkinsBuildSupported: boolean;
    plagiarismCheckSupported: boolean;
    packageNameRequired: boolean;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingLanguageFeatureService {
    private programmingLanguageFeatures: Map<ProgrammingLanguage, ProgrammingLanguageFeature> = new Map<ProgrammingLanguage, ProgrammingLanguageFeature>();

    constructor(private profileService: ProfileService) {
        this.profileService
            .getProfileInfo()
            .pipe(
                tap((info: ProfileInfo) => {
                    info.programmingLanguageFeatures.forEach((programmingLanguageFeature) => {
                        this.programmingLanguageFeatures.set(programmingLanguageFeature.programmingLanguage, programmingLanguageFeature);
                    });
                }),
            )
            .subscribe();
    }

    public getProgrammingLanguageFeature(programmingLanguage: ProgrammingLanguage): ProgrammingLanguageFeature {
        return this.programmingLanguageFeatures.get(programmingLanguage)!;
    }
}
