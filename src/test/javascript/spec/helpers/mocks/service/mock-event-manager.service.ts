import { SpyObject } from '../../spyobject';
import { JhiEventManager } from 'ng-jhipster';
import { SinonStub } from 'sinon';

export class MockEventManager extends SpyObject {
    broadcastSpy: SinonStub;

    constructor() {
        super(JhiEventManager);
        this.broadcastSpy = this.spy('broadcast').andReturn(this);
    }
}
