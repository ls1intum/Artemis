import { InjectionToken } from '@angular/core';

// Create the injection token
export const NG1WEBSOCKET_SERVICE = new InjectionToken<any>('NG1WEBSOCKET_SERVICE');

export function ng1ServerFactory(injector: any) {
    // This is the name of the authentication service in the ng1 app
    // Allows us to upgrade the service and use it in the ng5 app
    return injector.get('JhiWebsocketService');
}

// This will get injected as provider within the app.module.ts
export const ng1JhiWebsocketService = {
    provide: NG1WEBSOCKET_SERVICE,
    useFactory: ng1ServerFactory,
    deps: ['$injector']
};
