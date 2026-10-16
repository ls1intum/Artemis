import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExportButtonComponent } from 'app/shared-ui/export/button/export-button.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { CsvExportOptions, ExportDialogCloseResult } from 'app/shared-ui/export/modal/export-modal.component';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';

describe('ExportButtonComponent', () => {
    let fixture: ComponentFixture<ExportButtonComponent>;
    let comp: ExportButtonComponent;
    let dialogService: DialogService;
    let dialogClose: Subject<ExportDialogCloseResult>;

    beforeEach(async () => {
        dialogClose = new Subject<ExportDialogCloseResult>();
        await TestBed.configureTestingModule({
            imports: [ExportButtonComponent],
            providers: [
                { provide: DialogService, useValue: { open: vi.fn().mockReturnValue({ onClose: dialogClose } as unknown as DynamicDialogRef) } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExportButtonComponent);
        comp = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const dialogOpenStub = vi.spyOn(dialogService, 'open');

        fixture.detectChanges();
        comp.openExportModal(new MouseEvent('click'));
        const csvExportButton = fixture.debugElement.query(By.css('button[tumAetUiButton]'));
        expect(csvExportButton).not.toBeNull();
        expect(csvExportButton.nativeElement.getAttribute('type')).toBe('button');
        expect(dialogOpenStub).toHaveBeenCalledOnce();
    });

    describe('buttonSize mapping to tumAetUiSize', () => {
        it('should map default buttonSize (MEDIUM) to default tumAetUiSize', () => {
            fixture.detectChanges();
            expect(comp.tumAetUiSize()).toBe('default');
            const buttonDir = fixture.debugElement.query(By.directive(TumAetUiButtonDirective)).injector.get(TumAetUiButtonDirective);
            expect(buttonDir.size()).toBe('default');
        });

        it('should map SMALL buttonSize to small tumAetUiSize', () => {
            fixture.componentRef.setInput('buttonSize', ButtonSize.SMALL);
            fixture.detectChanges();
            expect(comp.tumAetUiSize()).toBe('small');
            const buttonDir = fixture.debugElement.query(By.directive(TumAetUiButtonDirective)).injector.get(TumAetUiButtonDirective);
            expect(buttonDir.size()).toBe('small');
        });

        it('should map LARGE buttonSize to large tumAetUiSize', () => {
            fixture.componentRef.setInput('buttonSize', ButtonSize.LARGE);
            fixture.detectChanges();
            expect(comp.tumAetUiSize()).toBe('large');
            const buttonDir = fixture.debugElement.query(By.directive(TumAetUiButtonDirective)).injector.get(TumAetUiButtonDirective);
            expect(buttonDir.size()).toBe('large');
        });

        it('should map explicit MEDIUM buttonSize to default tumAetUiSize', () => {
            fixture.componentRef.setInput('buttonSize', ButtonSize.MEDIUM);
            fixture.detectChanges();
            expect(comp.tumAetUiSize()).toBe('default');
            const buttonDir = fixture.debugElement.query(By.directive(TumAetUiButtonDirective)).injector.get(TumAetUiButtonDirective);
            expect(buttonDir.size()).toBe('default');
        });
    });

    it('should emit onExport when dialog is closed with a valid result', () => {
        const onExportSpy = vi.spyOn(comp.onExport, 'emit');
        fixture.detectChanges();
        comp.openExportModal(new MouseEvent('click'));

        const expectedResult = {} as CsvExportOptions;
        dialogClose.next(expectedResult);

        expect(onExportSpy).toHaveBeenCalledWith(expectedResult);
    });

    it('should not emit onExport when dialog is cancelled', () => {
        const onExportSpy = vi.spyOn(comp.onExport, 'emit');
        fixture.detectChanges();
        comp.openExportModal(new MouseEvent('click'));

        dialogClose.next({ cancelled: true });

        expect(onExportSpy).not.toHaveBeenCalled();
    });
});
