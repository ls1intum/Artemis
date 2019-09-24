import { SpyObject } from './spyobject';
import { AccountService } from 'app/core/auth/account.service';
import Spy = jasmine.Spy;

export class MockAccountService extends SpyObject {
    getSpy: Spy;
    saveSpy: Spy;
    fakeResponse: any;
    hasAnyAuthorityDirectSpy: Spy;
    identitySpy: Spy;
    isAtLeastInstructorInCourseSpy: Spy;

    constructor() {
        super(AccountService);

        this.fakeResponse = null;
        this.getSpy = this.spy('get').andReturn(this);
        this.saveSpy = this.spy('save').andReturn(this);
        this.hasAnyAuthorityDirectSpy = this.spy('hasAnyAuthorityDirect').andReturn(this);
        this.identitySpy = this.spy('identity').andReturn(this);
        this.isAtLeastInstructorInCourseSpy = this.spy('isAtLeastInstructorInCourse').andReturn(this);
    }

    subscribe(callback: any) {
        callback(this.fakeResponse);
    }

    setResponse(json: any): void {
        this.fakeResponse = json;
    }
}
