import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CsvExportButtonComponent } from 'app/shared/export/csv-export-button.component';

describe('CsvExportButtonComponent', () => {
    let fixture: ComponentFixture<CsvExportButtonComponent>;
    let comp: CsvExportButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule)],
            declarations: [CsvExportButtonComponent, MockComponent(ButtonComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CsvExportButtonComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ result });

        comp.openCsvExportModal(new MouseEvent('click'));
        const csvExportButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(csvExportButton).not.toBe(null);
        expect(modalServiceOpenStub).toHaveBeenCalledTimes(1);
    });
});
