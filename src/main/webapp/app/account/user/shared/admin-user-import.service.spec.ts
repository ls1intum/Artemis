import { describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { User } from 'app/account/user/user.model';

describe('AdminUserService import users', () => {
    setupTestBed({ zoneless: true });

    it('should pass createInternalUsers=true as query parameter when requested', () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        const adminService = TestBed.inject(AdminUserService);
        const httpMock = TestBed.inject(HttpTestingController);
        const users: Partial<User>[] = [{ login: 'user1', password: 'secret123' }];

        adminService.importAll(users, true).subscribe((response) => {
            expect(response.body).toEqual([]);
        });

        const req = httpMock.expectOne((request) => request.method === 'POST' && request.url === 'api/account/admin/users/import');
        expect(req.request.body).toEqual(users);
        expect(req.request.params.get('createInternalUsers')).toBe('true');
        req.flush([]);
        httpMock.verify();
    });
});
