import { Credentials, IAuthServerProvider } from 'app/core';
import { of } from 'rxjs';

export class MockAuthServerProviderService implements IAuthServerProvider {
    getToken = () => 'abc';
    login = (credentials: Credentials) => of('abc');
    loginWithToken = (jwt: string, rememberMe: string) => Promise.resolve('abc');
    removeAuthTokenFromCaches = () => of(null);
    storeAuthenticationToken = (jwt: string, rememberMe: string) => {};
}
