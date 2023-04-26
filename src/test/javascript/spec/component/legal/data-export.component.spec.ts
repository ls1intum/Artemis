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
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DataExport } from 'app/entities/data-export.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';
import { expectElementToBeEnabled } from '../../helpers/utils/general.utils';

describe('DataExportComponent', () => {
    let fixture: ComponentFixture<DataExportComponent>;
    let component: DataExportComponent;
    let dataExportService: DataExportService;
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should request data export on button click', () => {
        const dataExportReturned = new DataExport();
        dataExportReturned.id = 1;
        dataExportReturned.user = new User();
        const date = new Date(2023, 4, 26);
        dataExportReturned.creationDate = dayjs(date);
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'requestDataExport').mockReturnValue(of(dataExportReturned));
        component.isAdmin = true;
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#request-data-export-btn');
        expectElementToBeEnabled(button);
        button.click();
        component.requestExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnce();
        expect(component.canDownload).toBeTrue();
    });
    it('should download data export on button click', () => {
        const dataExportServiceSpy = jest.spyOn(dataExportService, 'downloadDataExport').mockReturnValue(of<HttpResponse<Blob>>({} as unknown as HttpResponse<Blob>));
        component.isAdmin = false;
        component.canDownload = true;
        component.dataExportId = 1;
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#download-data-export-btn');
        button.click();
        component.downloadDataExport();
        expect(dataExportServiceSpy).toHaveBeenCalledOnceWith(1);
    });
});
