import { SpyObject } from '../../spyobject';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { of } from 'rxjs';
import { SinonStub } from 'sinon';

export class MockLanguageService extends SpyObject {
    getCurrentLanguageSpy: SinonStub;

    constructor() {
        super(JhiLanguageService);

        this.getCurrentLanguageSpy = this.spy('getCurrentLanguage').andReturn('en');
    }
}

export class MockLanguageHelper extends SpyObject {
    getAllSpy: SinonStub;
    fakeResponse: 'en';

    constructor() {
        super(JhiLanguageHelper);

        this.getAllSpy = this.spy('getAll').andReturn(Promise.resolve(['en', 'fr']));
    }

    get language() {
        return of(this.fakeResponse);
    }
}
