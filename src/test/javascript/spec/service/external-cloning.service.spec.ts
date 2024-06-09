import { TestBed } from '@angular/core/testing';
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

    it('should build JetBrains url correctly', () => {
        const cloneUrl = baseUrl + '/git/repo.git';
        const expectedUrl = 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=https%3A%2F%2Fartemis.cit.tum.de%2Fgit%2Frepo.git';
        expect(service.buildJetbrainsUrl(cloneUrl)).toEqual(expectedUrl);
    });

    it('should return undefined when the argument is undefined', () => {
        expect(service.buildJetbrainsUrl(undefined)).toBeUndefined();
    });
});
