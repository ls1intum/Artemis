import { SpyObject } from './spyobject';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import Spy = jasmine.Spy;
import { of } from 'rxjs';

export class MockLanguageService extends SpyObject {
    getCurrentSpy: Spy;
    fakeResponse: any;

    constructor() {
        super(JhiLanguageService);

        this.fakeResponse = 'en';
        this.getCurrentSpy = this.spy('getCurrent').andReturn(Promise.resolve(this.fakeResponse));
    }

    init() {}

    changeLanguage(languageKey: string) {}

    setLocations(locations: string[]) {}

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
