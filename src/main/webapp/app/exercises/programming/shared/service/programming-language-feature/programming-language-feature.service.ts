import { Injectable } from '@angular/core';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { tap } from 'rxjs/operators';

/**
 * ProgrammingLanguageFeature, defined on the server, allows to customize the user interface during programming exercise generation
 * @readonly
 */
export type ProgrammingLanguageFeature = {
    programmingLanguage: ProgrammingLanguage;
    sequentialTestRuns: boolean;
    staticCodeAnalysis: boolean;
    plagiarismCheckSupported: boolean;
    packageNameRequired: boolean;
    checkoutSolutionRepositoryAllowed: boolean;
    projectTypes: ProjectType[];
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

    public supportsProgrammingLanguage(programmingLanguage: ProgrammingLanguage): boolean {
        // A programming language is supported if the server provided us with information about that language
        return this.programmingLanguageFeatures.has(programmingLanguage);
    }
}
