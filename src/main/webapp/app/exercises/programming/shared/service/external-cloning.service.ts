import { Injectable } from '@angular/core';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

const jetbrainsIdeas: { [key in ProgrammingLanguage]: string | undefined } = {
    [ProgrammingLanguage.JAVA]: 'idea',
    [ProgrammingLanguage.KOTLIN]: 'idea',
    [ProgrammingLanguage.PYTHON]: 'pycharm',
    [ProgrammingLanguage.C]: 'clion',
    [ProgrammingLanguage.HASKELL]: undefined,
    [ProgrammingLanguage.VHDL]: undefined,
    [ProgrammingLanguage.ASSEMBLER]: undefined,
    [ProgrammingLanguage.SWIFT]: undefined,
    [ProgrammingLanguage.OCAML]: undefined,
    [ProgrammingLanguage.EMPTY]: undefined,
};

@Injectable({ providedIn: 'root' })
export class ExternalCloningService {
    /**
     * Build source tree url.
     * @param baseUrl - the base url of the version control system
     * @param cloneUrl - url of the target.
     */
    buildSourceTreeUrl(baseUrl: string, cloneUrl: string | undefined): string | undefined {
        return cloneUrl ? `sourcetree://cloneRepo?type=stash&cloneUrl=${encodeURI(cloneUrl)}&baseWebUrl=${baseUrl}` : undefined;
    }

    getJetbrainsIdeForProgrammingLanguage(language?: ProgrammingLanguage): string | undefined {
        return language ? jetbrainsIdeas[language] : undefined;
    }

    /**
     * Builds a url to clone a Repository in IntelliJ
     * Structure: jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=:RepoUrl
     * @param cloneUrl
     */
    buildJetbrainsUrl(cloneUrl: string | undefined, programmingLangue: ProgrammingLanguage | undefined): string | undefined {
        // jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=git%40gitlab.db.in.tum.de%3Amoderndbs-2024%2Fexternal-sort.git
        if (!cloneUrl || !programmingLangue || !jetbrainsIdeas[programmingLangue]) {
            return undefined;
        }
        // TODO is this also idea.required.plugins for PyCharm?
        return `jetbrains://${jetbrainsIdeas[programmingLangue]}/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=${encodeURIComponent(cloneUrl)}`;
    }
}
