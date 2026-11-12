import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { UserService } from 'app/account/user/shared/user.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Authority } from 'app/foundation/constants/authority.constants';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

describe('User Service', () => {
    let service: UserService;
    let adminService: AdminUserService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(UserService);
        adminService = TestBed.inject(AdminUserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should return Authorities', () => {
            adminService.authorities().subscribe((_authorities) => {
                expect(_authorities).toEqual([Authority.STUDENT, Authority.ADMIN]);
            });
            const req = httpMock.expectOne({ method: 'GET' });

            req.flush([Authority.STUDENT, Authority.ADMIN]);
        });

        it('should call the admin URL to search users', () => {
            service.search('student').subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe('api/account/admin/users/search?loginOrName=student');
        });

        it('should call correct URL to initialize LTI user', () => {
            service.initializeLTIUser().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/account/users/initialize';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        const llmUsageUrl = '/api/account/users/select-llm-usage';

        it.each(Object.values(LLMSelectionDecision))('saves %s and returns the actual HTTP response', (selection) => {
            const next = vi.fn();
            service.updateLLMSelectionDecision(selection).subscribe({ next });

            const req = httpMock.expectOne({ method: 'PUT', url: llmUsageUrl });
            expect(req.request.body).toStrictEqual({ selection });
            const cacheControl = 'no-cache, no-store, max-age=0, must-revalidate';
            req.flush(null, { status: 200, statusText: 'OK', headers: { 'Cache-Control': cacheControl } });

            expect(next).toHaveBeenCalledExactlyOnceWith(expect.any(HttpResponse));
            const response: HttpResponse<void> = next.mock.calls[0][0];
            expect(response.status).toBe(200);
            expect(response.headers.get('Cache-Control')).toBe(cacheControl);
            expect(response.body).toBeNull();
        });

        it('propagates failure to save an AI usage decision', () => {
            const error = vi.fn();
            service.updateLLMSelectionDecision(LLMSelectionDecision.NO_AI).subscribe({ error });

            httpMock.expectOne({ method: 'PUT', url: llmUsageUrl }).flush(null, { status: 500, statusText: 'Internal Server Error' });

            expect(error).toHaveBeenCalledExactlyOnceWith(expect.any(HttpErrorResponse));
            expect(error.mock.calls[0][0].status).toBe(500);
        });
    });
});
