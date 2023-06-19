import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
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
import { HttpResponse } from '@angular/common/http';
import { DataExport } from 'app/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';

describe('DataExportComponent', () => {
    let fixture: ComponentFixture<DataExportComponent>;
    let component: DataExportComponent;
    let dataExportService: DataExportService;
    let accountService: AccountService;
    let alertService: AlertService;
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
            providers: [MockProvider(AlertService), { provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DataExportComponent);
                component = fixture.componentInstance;
                dataExportService = TestBed.inject(DataExportService);
                accountService = TestBed.inject(AccountService);
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should store the current user on init', () => {
        const user = new User();
        user.login = 'admin';
        user.id = 1;
        accountService.userIdentity = user;
        component.ngOnInit();
        expect(component.currentLogin).toBe('admin');
    });

    it('should call data export service when data export is requested', () => {
        const dataExportReturned = new DataExport();
        dataExportReturned.id = 1;
        dataExportReturned.user = new User();
        const date = new Date(2023, 4, 26);
        dataExportReturned.creationDate = dayjs(date);
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'requestDataExport').mockReturnValue(of(dataExportReturned));
        const alertServiceSpy = jest.spyOn(alertService, 'success');
        component.requestExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestSuccess');

        expect(component.canDownload).toBeTrue();
    });
    it('should call alert service when requesting fails', () => {
        jest.spyOn(dataExportService, 'requestDataExport').mockReturnValue(throwError({ status: 500 }));
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.requestExport();
        expect(alertServiceSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.dataExport.requestError');
        expect(component.canDownload).toBeFalse();
    });
    it('should call data export service when data export is downloaded', fakeAsync(() => {
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'downloadDataExport').mockReturnValue(of<HttpResponse<Blob>>({} as unknown as HttpResponse<Blob>));
        component.canDownload = true;
        component.dataExportId = 1;
        component.downloadDataExport();
        expect(dataExportServiceSpy).toHaveBeenCalledExactlyOnceWith(1);
    }));
});
