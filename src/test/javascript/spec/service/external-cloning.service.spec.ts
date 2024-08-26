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

    it('should build ide deeplink url correctly', () => {
        const cloneUrl = baseUrl + '/git/repo.git';
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        const expectedUrl = 'vscode://vscode.git/clone?url=https%3A%2F%2Fartemis.cit.tum.de%2Fgit%2Frepo.git';
        expect(service.buildIdeUrl(cloneUrl, ide)).toEqual(expectedUrl);
    });

    it('should return undefined when {cloneUrl} is not contained in ide deeplink', () => {
        const cloneUrl = baseUrl + '/git/repo.git';
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url=cloneUrl' };
        expect(service.buildIdeUrl(cloneUrl, ide)).toBeUndefined();
    });

    it('should return undefined when cloneUrl is undefined but ide is defined', () => {
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        expect(service.buildIdeUrl(undefined, ide)).toBeUndefined();
    });
});
