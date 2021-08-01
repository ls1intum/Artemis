import { SpyObject } from '../../spyobject';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { of } from 'rxjs';
import { SinonStub } from 'sinon';

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
