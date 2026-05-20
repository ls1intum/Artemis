import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { of, throwError } from 'rxjs';
import { DataExport } from 'app/core/shared/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DataExportComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<DataExportComponent>;
    let component: DataExportComponent;
    let dataExportService: DataExportService;
    let accountService: AccountService;
    let alertService: AlertService;
    let route: ActivatedRoute;
    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [DataExportComponent, MockComponent(ButtonComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockDirective(DeleteButtonDirective)],
            providers: [
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute(),
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();

        fixture = TestBed.createComponent(DataExportComponent);
        component = fixture.componentInstance;
        dataExportService = TestBed.inject(DataExportService);
        accountService = TestBed.inject(AccountService);
        alertService = TestBed.inject(AlertService);
        route = TestBed.inject(ActivatedRoute);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should store the current user on init', async () => {
        const user = new User();
        user.login = 'admin';
        user.id = 1;
        route.params = of({});
        accountService.userIdentity.set(user);
        component.ngOnInit();
        await fixture.whenStable();
        expect(component.currentLogin()).toBe('admin');
    });

    it('should call data export service when data export is requested', () => {
        const dataExportReturned = new DataExport();
        dataExportReturned.id = 1;
        dataExportReturned.user = new User();
        const date = new Date(2023, 4, 26);
        dataExportReturned.createdDate = dayjs(date);
        const dataExportServiceSpy = vi.spyOn(dataExportService, 'requestDataExport').mockReturnValue(of(dataExportReturned));
        const alertServiceSpy = vi.spyOn(alertService, 'success');
        component.requestExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.dataExport.requestSuccess');
    });

    it('should call alert service when requesting fails', () => {
        vi.spyOn(dataExportService, 'requestDataExport').mockReturnValue(throwError(() => ({ status: 500 })));
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        component.requestExport();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.dataExport.requestError');
        expect(component.canDownload()).toBe(false);
    });

    it('should call data export service when data export is downloaded', () => {
        const dataExportServiceSpy = vi.spyOn(dataExportService, 'downloadDataExport').mockImplementation(() => {});
        component.canDownload.set(true);
        component.dataExportId.set(1);
        component.downloadDataExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(dataExportServiceSpy).toHaveBeenCalledWith(1);
    });

    it.each([true, false])('should execute correct checks on init', async (downloadMode: boolean) => {
        if (downloadMode) {
            route.params = of({ id: 1 });
        } else {
            route.params = of({});
        }
        const canRequestSpy = vi.spyOn(dataExportService, 'canRequestDataExport').mockReturnValue(of(true));
        const canDownloadAnyDataExportSpy = vi.spyOn(dataExportService, 'canDownloadAnyDataExport').mockReturnValue(of({ id: 1 } as DataExport));
        const canDownloadSpecificDataExportSpy = vi.spyOn(dataExportService, 'canDownloadSpecificDataExport').mockReturnValue(of(true));
        component.ngOnInit();
        await fixture.whenStable();
        if (downloadMode) {
            expect(component.canRequestDataExport()).toBe(false);
            expect(canRequestSpy).not.toHaveBeenCalled();
            expect(canDownloadAnyDataExportSpy).not.toHaveBeenCalled();
            expect(canDownloadSpecificDataExportSpy).toHaveBeenCalled();
            expect(component.canDownload()).toBe(true);
            expect(component.dataExportId()).toBe(1);
        } else {
            expect(canRequestSpy).toHaveBeenCalled();
            expect(canDownloadAnyDataExportSpy).toHaveBeenCalled();
            expect(component.canRequestDataExport()).toBe(true);
            expect(component.canDownload()).toBe(true);
        }
    });

    it('should call data export service when data export for another user is requested', () => {
        const dataExportServiceSpy = vi.spyOn(dataExportService, 'requestDataExportForAnotherUser').mockReturnValue(of({} as DataExport));
        const alertServiceSpy = vi.spyOn(alertService, 'success');
        component.requestExportForAnotherUser('ge12abc');
        expect(dataExportServiceSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestForUserSuccess', { login: 'ge12abc' });
    });

    it('should call alert service when requesting for another user fails', () => {
        vi.spyOn(dataExportService, 'requestDataExportForAnotherUser').mockReturnValue(
            throwError(
                () =>
                    new HttpErrorResponse({
                        status: 500,
                    }),
            ),
        );
        const alertServiceSpy = vi.spyOn(alertService, 'error');
        component.requestExportForAnotherUser('ge12abc');
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestForUserError', { login: 'ge12abc' });
    });
});
