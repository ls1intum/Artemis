import { Credentials, IAuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { EMPTY } from 'rxjs';
import { of } from 'rxjs';

export class MockAuthServerProviderService implements IAuthServerProvider {
    login = (credentials: Credentials) => of(EMPTY);
    loginSAML2 = (rememberMe: boolean) => of(EMPTY);
    logout = () => of(EMPTY);
    clearCaches = () => of(undefined);
}
