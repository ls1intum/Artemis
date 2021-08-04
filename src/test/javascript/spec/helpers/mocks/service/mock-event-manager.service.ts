import { SpyObject } from '../../spyobject';
import { SinonSpy, SinonStub } from 'sinon';
import { EventManager } from 'app/core/util/event-manager.service';

export class MockEventManager extends SpyObject {
    broadcastSpy: SinonStub;
    destroySpy: SinonSpy;
    subscribeSpy: SinonSpy;

    constructor() {
        super(EventManager);
        this.broadcastSpy = this.spy('broadcast');
        this.subscribeSpy = this.spy('subscribe').andReturn(this);
        this.destroySpy = this.spy('destroy');
    }
}
