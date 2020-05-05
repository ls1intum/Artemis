import { SpyObject } from '../../spyobject';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { of } from 'rxjs';
import Spy = jasmine.Spy;

export class MockLanguageService extends SpyObject {
    getCurrentSpy: Spy;
    fakeResponse: any;

    constructor() {
        super(JhiLanguageService);

        this.fakeResponse = 'en';
        this.getCurrentSpy = this.spy('getCurrent').andReturn(Promise.resolve(this.fakeResponse));
    }

    init() {}

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    changeLanguage(languageKey: string) {}

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    setLocations(locations: string[]) {}

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    addLocation(location: string) {}

    reload() {}
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
