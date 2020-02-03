import { IAuthServerProvider } from 'app/core';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { of } from 'rxjs';

export class MockAuthServerProviderService implements IAuthServerProvider {
    getToken = () => 'abc';
    login = (credentials: Credentials) => of('abc');
    loginWithToken = (jwt: string, rememberMe: string) => Promise.resolve('abc');
    removeAuthTokenFromCaches = () => of(null);
    clearCaches = () => of(null);
    storeAuthenticationToken = (jwt: string, rememberMe: string) => {};
}
