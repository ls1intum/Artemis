import { SpyObject } from '../../spyobject';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { of } from 'rxjs';
import Spy = jasmine.Spy;

export class MockLanguageService extends SpyObject {
    getCurrentLanguageSpy: Spy;

    constructor() {
        super(JhiLanguageService);

        this.getCurrentLanguageSpy = this.spy('getCurrentLanguage').andReturn('en');
    }
}

export class MockLanguageHelper extends SpyObject {
    getAllSpy: Spy;
    fakeResponse: 'en';

    constructor() {
        super(JhiLanguageHelper);

        this.getAllSpy = this.spy('getAll').andReturn(Promise.resolve(['en', 'fr']));
    }

    get language() {
        return of(this.fakeResponse);
    }
}
