import { InjectionToken } from '@angular/core';

// Create the injection token
export const NG1TRANSLATEPARTIALLOADER_SERVICE = new InjectionToken<any>('NG1TRANSLATEPARTIALLOADER_SERVICE');

export function ng1ServerFactory(injector: any) {
    // This is the name of the authentication service in the ng1 app
    // Allows us to upgrade the service and use it in the ng5 app
    return injector.get('$translatePartialLoader');
}

// This will get injected as provider within the app.module.ts
export const ng1TranslatePartialLoaderService = {
    provide: NG1TRANSLATEPARTIALLOADER_SERVICE,
    useFactory: ng1ServerFactory,
    deps: ['$injector']
};
