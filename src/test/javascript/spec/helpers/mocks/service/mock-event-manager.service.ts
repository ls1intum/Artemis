import { SpyObject } from '../../spyobject';
import { JhiEventManager } from 'ng-jhipster';
import { SinonSpy, SinonStub } from 'sinon';

export class MockEventManager extends SpyObject {
    broadcastSpy: SinonStub;
    destroySpy: SinonSpy;
    constructor() {
        super(JhiEventManager);
        this.broadcastSpy = this.spy('broadcast').andReturn(this);
        this.destroySpy = this.spy('destroy');
    }
}
