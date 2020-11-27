import * as sinon from 'sinon';

export interface GuinessCompatibleSpy extends sinon.SinonStub {
    /** By chaining the spy with and.returnValue, all calls to the function will return a specific
     * value. */
    andReturn(val: any): GuinessCompatibleSpy;
    /** By chaining the spy with and.callFake, all calls to the spy will delegate to the supplied
     * function. */
    andCallFake(fn: Function): GuinessCompatibleSpy;
    /** removes all recorded calls */
    reset(): void;
}

export class SpyObject {
    constructor(type?: any) {
        if (type) {
            Object.keys(type.prototype).forEach((prop) => {
                let m = null;
                try {
                    m = type.prototype[prop];
                } catch (e) {
                    // As we are creating spys for abstract classes,
                    // these classes might have getters that throw when they are accessed.
                    // As we are only auto creating spys for methods, this
                    // should not matter.
                }
                if (typeof m === 'function') {
                    this.spy(prop);
                }
            });
        }
    }

    spy(name: string): GuinessCompatibleSpy {
        if (!this[name]) {
            this[name] = this.createGuinnessCompatibleSpy(name);
        }
        return this[name];
    }

    private createGuinnessCompatibleSpy(name: string): GuinessCompatibleSpy {
        const stub = {};
        stub[name] = function () {};
        const newSpy: GuinessCompatibleSpy = sinon.stub(stub, name as keyof typeof stub) as any;
        newSpy.andCallFake = newSpy.callsFake as any;
        newSpy.andReturn = newSpy.returns as any;
        return newSpy;
    }
}
