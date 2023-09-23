import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { AlertService } from 'app/core/util/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { of, throwError } from 'rxjs';
import { DataExport } from 'app/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('DataExportComponent', () => {
    let fixture: ComponentFixture<DataExportComponent>;
    let component: DataExportComponent;
    let dataExportService: DataExportService;
    let accountService: AccountService;
    let alertService: AlertService;
    let route: ActivatedRoute;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                DataExportComponent,
                MockComponent(ButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute(),
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DataExportComponent);
                component = fixture.componentInstance;
                dataExportService = TestBed.inject(DataExportService);
                accountService = TestBed.inject(AccountService);
                alertService = TestBed.inject(AlertService);
                route = TestBed.inject(ActivatedRoute);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should store the current user on init', fakeAsync(() => {
        const user = new User();
        user.login = 'admin';
        user.id = 1;
        route.params = of({});
        accountService.userIdentity = user;
        component.ngOnInit();
        tick();
        expect(component.currentLogin).toBe('admin');
    }));

    it('should call data export service when data export is requested', () => {
        const dataExportReturned = new DataExport();
        dataExportReturned.id = 1;
        dataExportReturned.user = new User();
        const date = new Date(2023, 4, 26);
        dataExportReturned.createdDate = dayjs(date);
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'requestDataExport').mockReturnValue(of(dataExportReturned));
        const alertServiceSpy = jest.spyOn(alertService, 'success');
        component.requestExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.dataExport.requestSuccess');
    });

    it('should call alert service when requesting fails', () => {
        jest.spyOn(dataExportService, 'requestDataExport').mockReturnValue(throwError({ status: 500 }));
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.requestExport();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.dataExport.requestError');
        expect(component.canDownload).toBeFalse();
    });

    it('should call data export service when data export is downloaded', () => {
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'downloadDataExport').mockImplementation();
        component.canDownload = true;
        component.dataExportId = 1;
        component.downloadDataExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(dataExportServiceSpy).toHaveBeenCalledWith(1);
    });

    it.each([true, false])('should execute correct checks on init', (downloadMode: boolean) => {
        if (downloadMode) {
            route.params = of({ id: 1 });
        }
        const canRequestSpy = jest.spyOn(dataExportService, 'canRequestDataExport').mockReturnValue(of(true));
        const canDownloadAnyDataExportSpy = jest.spyOn(dataExportService, 'canDownloadAnyDataExport').mockReturnValue(of({ id: 1 } as DataExport));
        const canDownloadSpecificDataExportSpy = jest.spyOn(dataExportService, 'canDownloadSpecificDataExport').mockReturnValue(of(true));
        component.ngOnInit();
        if (downloadMode) {
            expect(component.canRequestDataExport).toBeFalse();
            expect(canRequestSpy).not.toHaveBeenCalled();
            expect(canDownloadAnyDataExportSpy).not.toHaveBeenCalled();
            expect(canDownloadSpecificDataExportSpy).toHaveBeenCalledOnce();
            expect(canDownloadSpecificDataExportSpy).toHaveBeenCalledOnce();
            expect(component.canDownload).toBeTrue();
            expect(component.dataExportId).toBe(1);
        } else {
            expect(canRequestSpy).toHaveBeenCalledOnce();
            expect(canDownloadAnyDataExportSpy).toHaveBeenCalledOnce();
            expect(component.canRequestDataExport).toBeTrue();
            expect(component.canDownload).toBeTrue();
        }
    });

    it('should call data export service when data export for another user is requested', () => {
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'requestDataExportForAnotherUser').mockReturnValue(of({} as DataExport));
        const alertServiceSpy = jest.spyOn(alertService, 'success');
        component.requestExportForAnotherUser('ge12abc');
        expect(dataExportServiceSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestForUserSuccess', { login: 'ge12abc' });
    });

    it('should call alert service when requesting for another user fails', () => {
        jest.spyOn(dataExportService, 'requestDataExportForAnotherUser').mockReturnValue(throwError({ status: 500 }));
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.requestExportForAnotherUser('ge12abc');
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestForUserError', { login: 'ge12abc' });
    });
});
