import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingExerciseInstructorExerciseSharingComponent } from './programming-exercise-instructor-exercise-sharing.component';

describe('ProgrammingExercise Instructor Exercise Sharing', () => {
    let comp: ProgrammingExerciseInstructorExerciseSharingComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorExerciseSharingComponent>;
    let httpMock: HttpTestingController;
    let consoleError: any;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                httpMock = TestBed.inject(HttpTestingController);
                fixture = TestBed.createComponent(ProgrammingExerciseInstructorExerciseSharingComponent);
                comp = fixture.componentInstance;
            });
        consoleError = console.error;
        // Mock console.error to prevent error messages "navigation not implemented" in tests
        // This is necessary because the component uses window.location.href, which is not implemented in the test environment
        jest.spyOn(console, 'error').mockImplementation();
        Object.defineProperty(window, 'name', {
            configurable: true,
            enumerable: true,
            value: undefined,
        });
    });

    describe('Action method', () => {
        it('export to sharing initiation (success)', fakeAsync(() => {
            comp.exportExerciseToSharing(5);
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush('returnURL');
            tick();
        }));

        it('export to sharing initiation (success with defined sharing tab)', fakeAsync(() => {
            comp.sharingTab = {
                location: { href: '' },
                focus: () => {},
            } as WindowProxy;
            comp.exportExerciseToSharing(5);
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush('returnURL');
            tick();
        }));

        it('export to sharing initiation (fail Exercise not found)', fakeAsync(() => {
            comp.exportExerciseToSharing(5);
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush('Exercise not found', { status: 404, statusText: 'Not Found' });
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
        console.error = consoleError;
    });
});
