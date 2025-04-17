import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { SharingComponent } from 'app/sharing/sharing.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

describe('SharingComponent', () => {
    let fixture: ComponentFixture<SharingComponent>;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let alertService: AlertService;

    const route = {
        params: of({ basketToken: 'someBasketToken' }),
        queryParams: of({ returnURL: 'someReturnURL', apiBaseURL: 'someApiBaseURL', checksum: 'someChecksum' }),
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(ReactiveFormsModule)],
            declarations: [SharingComponent, TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },

                MockProvider(ProfileService),
                MockProvider(StaticContentService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SharingComponent);

                httpMock = TestBed.inject(HttpTestingController);
                accountService = TestBed.inject(AccountService);
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    const testBasket: ShoppingBasket = { exerciseInfo: [], userInfo: {}, tokenValidUntil: new Date(Date.now() + 60 * 60 * 1000) };

    const courses: Course[] = [
        { id: 1, title: 'testCouse 1' },
        { id: 2, title: 'testCouse 2' },
    ];

    it('loads baskets and courses, selects one, and navigates to import page', fakeAsync(() => {
        jest.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        fixture.detectChanges();
        tick();
        const basketUrl = `api/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        req.flush(testBasket);

        const courseReq = httpMock.expectOne((request) => request.url === 'api/core/courses/with-user-stats');

        courseReq.flush(courses);

        expect(fixture.componentInstance.getTokenExpiryDate()).toStrictEqual(testBasket.tokenValidUntil);

        // course not yet selected
        expect(fixture.componentInstance.courseId()).toStrictEqual(0);

        // further actions -> select course
        fixture.componentInstance.onCourseSelected(courses[0]);
        expect(fixture.componentInstance.selectedCourse).toStrictEqual(courses[0]);
        expect(fixture.componentInstance.courseId()).toStrictEqual(1);
        expect(fixture.componentInstance.trackId(0, courses[0])).toStrictEqual(1);
        fixture.componentInstance.sortRows(); // just for coverage ;-)

        // further actions -> select exercise

        fixture.componentInstance.onExerciseSelected(1);
        expect(fixture.componentInstance.sharingInfo.selectedExercise).toStrictEqual(1);

        // finally navigate to exercise import page
        fixture.componentInstance.navigateToImportFromSharing();
    }));

    it('failed init basket error', fakeAsync(() => {
        jest.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        // token expiry date not yet set
        expect(fixture.componentInstance.getTokenExpiryDate()).toBeBetween(new Date(Date.now() - 1000), new Date(Date.now() + 1000));
        const errorSpy = jest.spyOn(alertService, 'error');

        fixture.detectChanges();
        tick();
        const basketUrl = `api/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        const courseReq = httpMock.expectOne((request) => request.url === 'api/core/courses/with-user-stats');

        courseReq.flush(courses);

        req.flush(
            { message: 'Bakset not found' }, // error body
            {
                status: 404,
                statusText: 'Not Found',
            },
        );

        expect(errorSpy).toHaveBeenCalledOnce();
    }));

    it('failed init course load error', fakeAsync(() => {
        jest.spyOn(accountService, 'hasAnyAuthority').mockReturnValue(Promise.resolve(true));
        // token expiry date not yet set
        expect(fixture.componentInstance.getTokenExpiryDate()).toBeBetween(new Date(Date.now() - 1000), new Date(Date.now() + 1000));

        fixture.detectChanges();
        tick();
        const basketUrl = `api/sharing/import/basket?basketToken=${fixture.componentInstance.sharingInfo.basketToken}&returnURL=${fixture.componentInstance.sharingInfo.returnURL}&apiBaseURL=${fixture.componentInstance.sharingInfo.apiBaseURL}&checksum=${fixture.componentInstance.sharingInfo.checksum}`;
        const req = httpMock.expectOne({
            method: 'GET',
            url: basketUrl,
        });

        req.flush(testBasket);

        const courseReq = httpMock.expectOne((request) => request.url === 'api/core/courses/with-user-stats');

        const errorSpy = jest.spyOn(alertService, 'error');

        courseReq.flush({ message: 'Not Found' }, { status: 500, statusText: 'Some error' });

        expect(errorSpy).toHaveBeenCalledOnce();
    }));

    it(' basketInfo validation', fakeAsync(() => {
        let sharingInfo: SharingInfo = new SharingInfo();

        sharingInfo.basketToken = 'someBasketToken';
        sharingInfo.returnURL = 'someReturnURL';
        sharingInfo.checksum = 'someCheckSum1234abcd';
        sharingInfo.apiBaseURL = 'someBaseURL';

        expect(sharingInfo.isAvailable()).toBeTrue();

        sharingInfo.validate();

        sharingInfo.clear();
        try {
            sharingInfo.validate();
        } catch (err) {
            expect(err).toBeInstanceOf(Error);
            expect(err.message).toBe('Basket token is required');
        }
        try {
            sharingInfo.validate();
            fail('Error expected');
        } catch (err) {
            expect(err).toBeInstanceOf(Error);
            expect(err.message).toBe('Basket token is required');
        }
        try {
            sharingInfo.basketToken = 'someToken';
            // Api Base URL still undefined
            sharingInfo.validate();
            fail('Error expected');
        } catch (err) {
            expect(err).toBeInstanceOf(Error);
            expect(err.message).toBe('API base URL is required');
        }
    }));
});
