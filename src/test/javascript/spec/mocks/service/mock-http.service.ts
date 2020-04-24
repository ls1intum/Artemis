import { of } from 'rxjs';

export class MockHttpService {
    get = (url: string) => of();
    post = () => of();
    put = () => of();
    patch = () => of();
}
