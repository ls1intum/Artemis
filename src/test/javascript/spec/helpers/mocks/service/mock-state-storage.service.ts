import { SpyObject } from '../../spyobject';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { SinonStub } from 'sinon';

export class MockStateStorageService extends SpyObject {
    getUrlSpy: SinonStub;
    storeUrlSpy: SinonStub;

    constructor() {
        super(StateStorageService);
        this.setUrlSpy({});
        this.storeUrlSpy = this.spy('storeUrl').andReturn(this);
    }

    setUrlSpy(json: any) {
        this.getUrlSpy = this.spy('getUrl').andReturn(json);
    }

    setResponse(json: any): void {
        this.setUrlSpy(json);
    }
}
