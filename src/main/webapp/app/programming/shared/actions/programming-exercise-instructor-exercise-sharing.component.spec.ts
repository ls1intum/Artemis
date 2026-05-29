import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingExerciseInstructorExerciseSharingComponent } from './programming-exercise-instructor-exercise-sharing.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { Component, signal } from '@angular/core';

@Component({
    template: `<jhi-programming-exercise-instructor-exercise-sharing [exerciseId]="testValue()"></jhi-programming-exercise-instructor-exercise-sharing>`,
    imports: [ProgrammingExerciseInstructorExerciseSharingComponent],
})
class TestHostComponent {
    testValue = signal<number | undefined>(5);
}

describe('ProgrammingExercise Instructor Exercise Sharing', () => {
    setupTestBed({ zoneless: true });

    let hostFixture: ComponentFixture<TestHostComponent>;

    let comp: ProgrammingExerciseInstructorExerciseSharingComponent;
    let httpMock: HttpTestingController;
    let consoleError: typeof console.error;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                httpMock = TestBed.inject(HttpTestingController);
                hostFixture = TestBed.createComponent(TestHostComponent);

                hostFixture.detectChanges();
                const childDebugEl = hostFixture.debugElement.query(By.directive(ProgrammingExerciseInstructorExerciseSharingComponent));
                comp = childDebugEl.componentInstance as ProgrammingExerciseInstructorExerciseSharingComponent;
            });
        consoleError = console.error;
        // Mock console.error to prevent error messages "navigation not implemented" in tests
        // This is necessary because the component uses window.location.href, which is not implemented in the test environment
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        Object.defineProperty(window, 'name', {
            configurable: true,
            enumerable: true,
            writable: true,
            value: undefined,
        });
    });

    describe('Action method', () => {
        it('export to sharing initiation (success)', async () => {
            comp.exportExerciseToSharing();
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush('returnURL');
            await hostFixture.whenStable();
        });

        it('export to sharing initiation (success with defined sharing tab)', async () => {
            comp.sharingTab = {
                location: { href: '' },
                focus: () => {},
            } as WindowProxy;
            comp.exportExerciseToSharing();
            const req = httpMock.expectOne((request) => request.method === 'POST' && request.url.includes('/programming/sharing/export/5'));
            expect(req.request.body).toBe(window.location.href);
            req.flush('returnURL');
            await hostFixture.whenStable();
        });

        it('export to sharing initiation (fail Exercise not found)', async () => {
            const alertService: AlertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(alertService, 'error');

            comp.exportExerciseToSharing();
            const req = httpMock.expectOne((request) => request.method === 'POST' && request.url.includes('/programming/sharing/export/5'));
            req.flush('Exercise not found', { status: 404, statusText: 'Not Found' });

            await hostFixture.whenStable();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.sharing.error.export', { message: 'Exercise not found' });
        });
    });

    afterEach(() => {
        httpMock.verify();
        console.error = consoleError;
        vi.restoreAllMocks();
    });
});
