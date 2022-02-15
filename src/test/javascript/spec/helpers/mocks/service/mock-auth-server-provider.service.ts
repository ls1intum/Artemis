import { Credentials, IAuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { EMPTY } from 'rxjs';
import { of } from 'rxjs';

export class MockAuthServerProviderService implements IAuthServerProvider {
    getToken = () => 'abc';
    login = (credentials: Credentials) => EMPTY;
    loginWithToken = (jwt: string, rememberMe: boolean) => Promise.resolve('abc');
    removeAuthTokenFromCaches = () => of(undefined);
    clearCaches = () => of(undefined);
    storeAuthenticationToken = (jwt: string, rememberMe: boolean) => {};
}
