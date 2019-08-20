import { CookieOptions, ICookieService } from 'ngx-cookie';

export class MockCookieService implements ICookieService {
    get(key: string): string {
        return '';
    }

    getAll(): Object {
        return undefined;
    }

    getObject(key: string): Object {
        return undefined;
    }

    put(key: string, value: string, options?: CookieOptions): void {}

    putObject(key: string, value: Object, options?: CookieOptions): void {}

    remove(key: string, options?: CookieOptions): void {}

    removeAll(options?: CookieOptions): void {}
}
