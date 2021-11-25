import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';

@Injectable({ providedIn: 'root' })
export class MockCookieService extends CookieService {
    get(key: string): string {
        return '';
    }

    getAll(): any {
        return {};
    }

    getObject(key: string): Object {
        return {};
    }

    put(key: string, value: string, options?: any): void {}

    putObject(key: string, value: Object, options?: any): void {}

    remove(key: string, options?: any): void {}

    removeAll(options?: any): void {}
}
