import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { StaticContentService } from 'app/foundation/service/static-content.service';
import { MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { SharingComponent } from 'app/sharing/sharing.component';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

/** Drains all pending micro- and macrotasks so chained promises (e.g. checkLogin) resolve under fake timers. */
const flushPromises = () => vi.advanceTimersByTimeAsync(0);

describe('SharingComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SharingComponent>;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let alertService: AlertService;
    let router: { navigate: ReturnType<typeof vi.fn> };

    const route = {
        params: of({ basketToken: 'someBasketToken' }),
        queryParams: of({ returnURL: 'someReturnURL', apiBaseURL: 'someApiBaseURL', checksum: 'someChecksum' }),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        // Freeze the clock so the time-based `getBasketTokenExpiryDate()` fallback (`new Date()`) renders a
        // stable value across Angular's dev-mode double change-detection pass (otherwise it throws NG0100).
        vi.useFakeTimers();
        const routerMock = {
            navigate: vi.fn().mockResolvedValue(true), // mock a successful navigation
        };
        await TestBed.configureTestingModule({
            imports: [SharingComponent, MockModule(ReactiveFormsModule), TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: routerMock },
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,

                MockProvider(ProfileService),
                MockProvider(StaticContentService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(SharingComponent);

        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router) as unknown as { navigate: ReturnType<typeof vi.fn> };
    });

    afterEach(() => {
        httpMock.verify();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    const testBasket: ShoppingBasket = { exerciseInfo: [], userInfo: { email: 'test@banana.com' }, tokenValidUntil: new Date(Date.now() + 60 * 60 * 1000) };

    const courses: Course[] = [
        { id: 1, title: 'testCourse 1' },
        { id: 2, title: 'testCourse 2' },
    ];

    it('loads baskets and courses, selects one, and navigates to import page', async () => {
        // given
        vi.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        vi.spyOn(alertService, 'error');
        fixture.detectChanges();
        await flushPromises();

        // when
        const basketUrl = `api/programming/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        req.flush(testBasket);

        const courseReq = httpMock.expectOne((request) => request.url === 'api/course/courses/course-management-overview');

        courseReq.flush(courses);

        // then
        expect(fixture.componentInstance.getBasketTokenExpiryDate()).toStrictEqual(testBasket.tokenValidUntil);

        // course not yet selected
        expect(fixture.componentInstance.courseId()).toBe(0);

        // further actions -> select course
        fixture.componentInstance.onCourseSelected(courses[0]);
        expect(fixture.componentInstance.selectedCourse).toStrictEqual(courses[0]);
        expect(fixture.componentInstance.courseId()).toBe(1);
        expect(fixture.componentInstance.trackId(0, courses[0])).toBe(1);
        fixture.componentInstance.sortRows(); // just for coverage ;-)

        // WHEN further actions -> select exercise

        fixture.componentInstance.onExerciseSelected(1);

        // THEN
        expect(fixture.componentInstance.sharingInfo.selectedExercise).toBe(1);

        // WHEN finally navigate to exercise import  page
        fixture.componentInstance.navigateToImportFromSharing();

        await flushPromises();

        // THEN
        expect(router.navigate).toHaveBeenCalledOnce();

        await flushPromises();

        // WHEN unsuccessful navigation
        router.navigate = vi.fn().mockResolvedValue(false);

        fixture.componentInstance.navigateToImportFromSharing();

        await flushPromises();

        // THEN

        expect(alertService.error).toHaveBeenCalledOnce();

        // WHEN unsuccessful navigation
        // alertService.error.mockClear();
        router.navigate = vi.fn().mockRejectedValue(false);

        fixture.componentInstance.navigateToImportFromSharing();

        await flushPromises();

        // THEN
        expect(alertService.error).toHaveBeenCalled();
    });

    it('test formatted ExpiryDate', () => {
        const someValidityDate = new Date('1.1.2025 17:30');
        fixture.componentInstance.shoppingBasket = {
            exerciseInfo: [],
            userInfo: { email: 'test@banana.com' },
            tokenValidUntil: someValidityDate,
        };
        expect(fixture.componentInstance.formattedExpiryDate).toBe(someValidityDate.toLocaleString());
    });

    it('failed init basket error', async () => {
        vi.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        // token expiry date not yet set
        const tokenExpiryDate = fixture.componentInstance.getBasketTokenExpiryDate();
        expect(tokenExpiryDate.getTime()).toBeGreaterThanOrEqual(Date.now() - 1000);
        expect(tokenExpiryDate.getTime()).toBeLessThanOrEqual(Date.now() + 1000);
        const errorSpy = vi.spyOn(alertService, 'error');

        fixture.detectChanges();
        await flushPromises();
        const basketUrl = `api/programming/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        const courseReq = httpMock.expectOne((request) => request.url === 'api/course/courses/course-management-overview');

        courseReq.flush(courses);

        req.flush(
            { message: 'Basket not found' }, // error body
            {
                status: 404,
                statusText: 'Not Found',
            },
        );

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('failed init course load error', async () => {
        vi.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        // token expiry date not yet set
        expect(fixture.componentInstance.getBasketTokenExpiryDate().getTime()).toBeGreaterThanOrEqual(Date.now() - 1000);
        expect(fixture.componentInstance.getBasketTokenExpiryDate().getTime()).toBeLessThanOrEqual(Date.now() + 1000);

        fixture.detectChanges();
        await flushPromises();
        const basketUrl = `api/programming/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        req.flush(testBasket);

        const courseReq = httpMock.expectOne((request) => request.url === 'api/course/courses/course-management-overview');

        const errorSpy = vi.spyOn(alertService, 'error');

        courseReq.flush({ message: 'Not Found' }, { status: 500, statusText: 'Some error' });

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('missing Baskettoken', () => {
        const sharingInfo: SharingInfo = new SharingInfo();

        // sharingInfo.basketToken will be '' by default;
        sharingInfo.returnURL = 'someReturnURL';
        sharingInfo.checksum = 'someCheckSum1234abcd';
        sharingInfo.apiBaseURL = 'someBaseURL';

        expect(() => sharingInfo.validate()).toThrow('Basket token is required');

        sharingInfo.clear();
        expect(() => sharingInfo.validate()).toThrow('Basket token is required');

        sharingInfo.basketToken = 'someToken';
        expect(() => sharingInfo.validate()).toThrow('API base URL is required');
    });
});
