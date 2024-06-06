import { TestBed } from '@angular/core/testing';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ExternalCloningService } from 'app/exercises/programming/shared/service/external-cloning.service';

describe('ExternalCloningService', () => {
    let service: ExternalCloningService;
    const baseUrl = 'https://artemis.cit.tum.de';

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(ExternalCloningService);
    });

    it('should build source tree url correctly', () => {
        const cloneUrl = baseUrl + '/git/reo.git';
        const expectedUrl = `sourcetree://cloneRepo?type=stash&cloneUrl=https://artemis.cit.tum.de/git/reo.git&baseWebUrl=https://artemis.cit.tum.de`;
        expect(service.buildSourceTreeUrl(baseUrl, cloneUrl)).toEqual(expectedUrl);
    });

    it('should return undefined when cloneUrl is undefined', () => {
        expect(service.buildSourceTreeUrl(baseUrl, undefined)).toBeUndefined();
    });

    it('should return correct JetBrains IDE for given programming language', () => {
        expect(service.getJetbrainsIdeForProgrammingLanguage(ProgrammingLanguage.JAVA)).toBe('idea');
        expect(service.getJetbrainsIdeForProgrammingLanguage(ProgrammingLanguage.KOTLIN)).toBe('idea');
        expect(service.getJetbrainsIdeForProgrammingLanguage(ProgrammingLanguage.PYTHON)).toBe('pycharm');
        expect(service.getJetbrainsIdeForProgrammingLanguage(ProgrammingLanguage.C)).toBe('clion');
    });

    it('should build JetBrains url correctly', () => {
        const cloneUrl = baseUrl + '/git/repo.git';
        const expectedUrl = 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=https%3A%2F%2Fartemis.cit.tum.de%2Fgit%2Frepo.git';
        expect(service.buildJetbrainsUrl(cloneUrl, ProgrammingLanguage.JAVA)).toEqual(expectedUrl);
    });

    it('should return undefined when any argument is undefined', () => {
        const cloneUrl = 'http://clone.url';
        expect(service.buildJetbrainsUrl(cloneUrl, undefined)).toBeUndefined();
        expect(service.buildJetbrainsUrl(undefined, ProgrammingLanguage.JAVA)).toBeUndefined();
    });

    it('should return undefined when not fitting JetBrains IDE is available', () => {
        expect(service.buildJetbrainsUrl(baseUrl, ProgrammingLanguage.HASKELL)).toBeUndefined();
    });
});
