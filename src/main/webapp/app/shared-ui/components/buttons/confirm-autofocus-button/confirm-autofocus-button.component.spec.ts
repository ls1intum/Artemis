import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmAutofocusButtonComponent } from 'app/shared-ui/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TemplateRef } from '@angular/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { ConfirmAutofocusModalResult } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';

describe('ConfirmAutofocusButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ConfirmAutofocusButtonComponent>;
    let comp: ConfirmAutofocusButtonComponent;
    let dialogService: DialogService;
    let onClose: Subject<ConfirmAutofocusModalResult | undefined>;
    let dialogRef: DynamicDialogRef;

    beforeEach(() => {
        onClose = new Subject<ConfirmAutofocusModalResult | undefined>();
        dialogRef = { onClose } as unknown as DynamicDialogRef;
        TestBed.configureTestingModule({
            imports: [ConfirmAutofocusButtonComponent],
            providers: [
                { provide: DialogService, useValue: { open: vi.fn().mockReturnValue(dialogRef) } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ConfirmAutofocusButtonComponent);
        comp = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    describe('default values', () => {
        it('should have default disabled as false', () => {
            expect(comp.disabled()).toBeFalsy();
        });

        it('should have default isLoading as false', () => {
            expect(comp.isLoading()).toBeFalsy();
        });

        it('should have default btnType as PRIMARY', () => {
            expect(comp.btnType()).toBe(ButtonType.PRIMARY);
        });
    });

    describe('inputs', () => {
        it('should accept icon input', () => {
            fixture.componentRef.setInput('icon', faCheck);
            fixture.detectChanges();
            expect(comp.icon()).toEqual(faCheck);
        });

        it('should accept title input', () => {
            fixture.componentRef.setInput('title', 'Test Title');
            fixture.detectChanges();
            expect(comp.title()).toBe('Test Title');
        });

        it('should accept tooltip input', () => {
            fixture.componentRef.setInput('tooltip', 'Test Tooltip');
            fixture.detectChanges();
            expect(comp.tooltip()).toBe('Test Tooltip');
        });

        it('should accept confirmationTitle input', () => {
            fixture.componentRef.setInput('confirmationTitle', 'Confirm Title');
            fixture.detectChanges();
            expect(comp.confirmationTitle()).toBe('Confirm Title');
        });

        it('should accept confirmationText input', () => {
            fixture.componentRef.setInput('confirmationText', 'Confirm Text');
            fixture.detectChanges();
            expect(comp.confirmationText()).toBe('Confirm Text');
        });

        it('should accept confirmationTitleTranslationParams input', () => {
            const params = { name: 'Test' };
            fixture.componentRef.setInput('confirmationTitleTranslationParams', params);
            fixture.detectChanges();
            expect(comp.confirmationTitleTranslationParams()).toEqual(params);
        });
    });

    describe('onOpenConfirmationModal', () => {
        const dialogData = () => vi.mocked(dialogService.open).mock.calls[0][1]?.data as Record<string, unknown>;

        it('should open modal with plain text', () => {
            const openSpy = vi.spyOn(dialogService, 'open');
            fixture.componentRef.setInput('confirmationText', 'Plain text content');
            fixture.componentRef.setInput('confirmationTitle', 'Test Title');
            fixture.componentRef.setInput('textIsMarkdown', false);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(dialogData().text).toBe('Plain text content');
            expect(dialogData().textIsMarkdown).toBeFalsy();
            expect(dialogData().title).toBe('Test Title');
        });

        it('should open modal with markdown text', () => {
            const openSpy = vi.spyOn(dialogService, 'open');
            fixture.componentRef.setInput('confirmationText', '**Bold text**');
            fixture.componentRef.setInput('confirmationTitle', 'Test Title');
            fixture.componentRef.setInput('textIsMarkdown', true);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(dialogData().textIsMarkdown).toBeTruthy();
            expect(dialogData().text).toContain('<strong>Bold text</strong>');
        });

        it('should set translateText to true when specified', () => {
            vi.spyOn(dialogService, 'open');
            fixture.componentRef.setInput('translateText', true);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(dialogData().translateText).toBeTruthy();
        });

        it('should set translateText to false when not specified', () => {
            vi.spyOn(dialogService, 'open');
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(dialogData().translateText).toBeFalsy();
        });

        it('should pass titleTranslationParams to modal', () => {
            vi.spyOn(dialogService, 'open');
            const params = { name: 'Test' };
            fixture.componentRef.setInput('confirmationTitleTranslationParams', params);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(dialogData().titleTranslationParams).toEqual(params);
        });

        it('should pass content ref to modal', () => {
            vi.spyOn(dialogService, 'open');

            const mockContent = {} as TemplateRef<any>;

            // viewChild() returns a callable signal; override it for this test
            (comp as any).content = () => mockContent;

            comp.onOpenConfirmationModal();

            expect(dialogData().contentRef).toBe(mockContent);
        });

        it('should emit onConfirm when modal is confirmed', () => {
            vi.spyOn(dialogService, 'open');
            const confirmSpy = vi.spyOn(comp.onConfirm, 'emit');

            comp.onOpenConfirmationModal();
            onClose.next({ confirmed: true });

            expect(confirmSpy).toHaveBeenCalled();
        });

        it('should emit onCancel when modal is dismissed', () => {
            vi.spyOn(dialogService, 'open');
            const cancelSpy = vi.spyOn(comp.onCancel, 'emit');

            comp.onOpenConfirmationModal();
            onClose.next(undefined);

            expect(cancelSpy).toHaveBeenCalled();
        });
    });
});
