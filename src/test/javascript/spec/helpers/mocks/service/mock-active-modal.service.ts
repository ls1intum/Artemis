import { SpyObject } from '../../spyobject';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { SinonStub } from 'sinon';

export class MockActiveModal extends SpyObject {
    dismissSpy: SinonStub;
    closeSpy: SinonStub;

    constructor() {
        super(NgbActiveModal);
        this.dismissSpy = this.spy('dismiss').andReturn(this);
        this.closeSpy = this.spy('close').andReturn(this);
    }
}
