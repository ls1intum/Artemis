import { TestBed } from '@angular/core/testing';
import { UserService } from 'app/core/user/shared/user.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Authority } from 'app/shared/constants/authority.constants';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { provideHttpClient } from '@angular/common/http';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

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
    });

    describe('Service methods', () => {
        it('should return Authorities', () => {
            adminService.authorities().subscribe((_authorities) => {
                expect(_authorities).toEqual([Authority.USER, Authority.ADMIN]);
            });
            const req = httpMock.expectOne({ method: 'GET' });

            req.flush([Authority.USER, Authority.ADMIN]);
        });

        it('should call correct URL to initialize LTI user', () => {
            service.initializeLTIUser().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/core/users/initialize';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should call correct URL to accept external LLM', () => {
            service.updateLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI).subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/core/users/select-llm-usage';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });
    });
});
