import { Injectable, inject } from '@angular/core';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
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
    projectTypes?: ProjectType[];
    auxiliaryRepositoriesSupported: boolean;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingLanguageFeatureService {
    private profileService = inject(ProfileService);

    private programmingLanguageFeatures: Map<ProgrammingLanguage, ProgrammingLanguageFeature> = new Map<ProgrammingLanguage, ProgrammingLanguageFeature>();

    constructor() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            profileInfo.programmingLanguageFeatures.forEach((programmingLanguageFeature) => {
                this.programmingLanguageFeatures.set(programmingLanguageFeature.programmingLanguage, programmingLanguageFeature);
            });
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
