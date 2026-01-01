import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ExportButtonComponent } from 'app/shared/export/button/export-button.component';

describe('ExportButtonComponent', () => {
    let fixture: ComponentFixture<ExportButtonComponent>;
    let comp: ExportButtonComponent;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(NgbModule)],
            declarations: [ExportButtonComponent, MockComponent(ButtonComponent)],
        }).compileComponents();
        fixture = TestBed.createComponent(ExportButtonComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ result });

        comp.openExportModal(new MouseEvent('click'));
        const csvExportButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(csvExportButton).not.toBeNull();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
    });
});
