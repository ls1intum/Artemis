import { SpyObject } from '../../spyobject';
import { SinonSpy, SinonStub } from 'sinon';
import { EventManager } from 'app/core/util/event-manager.service';

export class MockEventManager extends SpyObject {
    broadcastSpy: SinonStub;
    destroySpy: SinonSpy;
    constructor() {
        super(EventManager);
        this.broadcastSpy = this.spy('broadcast').andReturn(this);
        this.destroySpy = this.spy('destroy');
    }
}
