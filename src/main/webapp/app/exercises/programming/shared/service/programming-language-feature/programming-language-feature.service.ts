import { Injectable } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { tap } from 'rxjs/operators';

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
    checkoutSolutionRepositoryAllowed: boolean;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingLanguageFeatureService {
    private programmingLanguageFeatures: Map<ProgrammingLanguage, ProgrammingLanguageFeature> = new Map<ProgrammingLanguage, ProgrammingLanguageFeature>();
    private bambooBuildSupported = false;
    private jenkinsBuildSupported = false;

    constructor(private profileService: ProfileService) {
        this.profileService
            .getProfileInfo()
            .pipe(
                tap((info: ProfileInfo) => {
                    this.bambooBuildSupported = info.activeProfiles.includes('bamboo');
                    this.jenkinsBuildSupported = info.activeProfiles.includes('jenkins');
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

    public getBambooBuildSupported(): boolean {
        return this.bambooBuildSupported;
    }

    public getJenkinsBuildSupported(): boolean {
        return this.jenkinsBuildSupported;
    }

    public supportsProgrammingLanguage(programmingLanguage: ProgrammingLanguage): boolean {
        const programmingLanguageFeature = this.getProgrammingLanguageFeature(programmingLanguage);
        // A programming language is supported if it is either available on Bamboo and we are running Bamboo or available on Jenkins and we are running Jenkins
        return (programmingLanguageFeature.bambooBuildSupported && this.bambooBuildSupported) || (programmingLanguageFeature.jenkinsBuildSupported && this.jenkinsBuildSupported);
    }
}
