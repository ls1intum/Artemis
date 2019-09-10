import { SpyObject } from './spyobject';
import { AccountService } from 'app/core/auth/account.service';
import Spy = jasmine.Spy;
import { User } from 'app/core';

export class MockAccountService extends SpyObject {
    getSpy: Spy;
    saveSpy: Spy;
    fakeResponse: any;
    hasAnyAuthorityDirectSpy: Spy;
    fakeUser: User;

    constructor() {
        super(AccountService);

        this.fakeResponse = null;
        this.getSpy = this.spy('get').andReturn(this);
        this.saveSpy = this.spy('save').andReturn(this);
        this.hasAnyAuthorityDirectSpy = this.spy('hasAnyAuthorityDirect').andReturn(this);
    }

    subscribe(callback: any) {
        callback(this.fakeResponse);
    }

    setResponse(json: any): void {
        this.fakeResponse = json;
    }

    setUser(user: User): void {
        this.fakeUser = user;
    }

    identity(): Promise<User | null> {
        return Promise.resolve(this.fakeUser);
    }
}
