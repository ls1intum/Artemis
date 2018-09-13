import { InjectionToken } from '@angular/core';

/** Create the injection token */
export const NG1AUTH_SERVICE = new InjectionToken<any>('NG1AUTH_SERVICE');

/**
 * @function ng1ServerFactory
 * @param injector
 * @desc Fetches the ng1 service (matched by name) and creates the injector for it.
 */
export function ng1ServerFactory(injector: any) {
    /** This is the name of the authentication service in the ng1 app
     *  Allows us to upgrade the service and use it in the ng5 app */
    return injector.get('AuthServerProvider');
}

/** This will get injected as provider within the app.module.ts */
export const ng1AuthServiceProvider = {
    /**
     * The @param 'provide' value, which is 'NG1AUTH_SERVICE', can be imported and used as service in any component.
     */
    provide: NG1AUTH_SERVICE,
    useFactory: ng1ServerFactory,
    deps: ['$injector']
};
