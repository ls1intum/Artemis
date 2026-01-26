import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TemplateRef } from '@angular/core';

describe('ConfirmAutofocusButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ConfirmAutofocusButtonComponent>;
    let comp: ConfirmAutofocusButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ConfirmAutofocusButtonComponent],
            providers: [NgbModal, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ConfirmAutofocusButtonComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
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
        let mockModalRef: Partial<NgbModalRef>;

        beforeEach(() => {
            mockModalRef = {
                componentInstance: {},
                result: Promise.resolve(),
            };
        });

        it('should open modal with plain text', async () => {
            const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            fixture.componentRef.setInput('confirmationText', 'Plain text content');
            fixture.componentRef.setInput('confirmationTitle', 'Test Title');
            fixture.componentRef.setInput('textIsMarkdown', false);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(mockModalRef.componentInstance!.text).toBe('Plain text content');
            expect(mockModalRef.componentInstance!.textIsMarkdown).toBeFalsy();
            expect(mockModalRef.componentInstance!.title).toBe('Test Title');
        });

        it('should open modal with markdown text', async () => {
            const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            fixture.componentRef.setInput('confirmationText', '**Bold text**');
            fixture.componentRef.setInput('confirmationTitle', 'Test Title');
            fixture.componentRef.setInput('textIsMarkdown', true);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(mockModalRef.componentInstance!.textIsMarkdown).toBeTruthy();
        });

        it('should set translateText to true when specified', () => {
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            fixture.componentRef.setInput('translateText', true);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.translateText).toBeTruthy();
        });

        it('should set translateText to false when not specified', () => {
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            fixture.componentRef.setInput('translateText', undefined);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.translateText).toBeFalsy();
        });

        it('should pass titleTranslationParams to modal', () => {
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const params = { name: 'Test' };
            fixture.componentRef.setInput('confirmationTitleTranslationParams', params);
            fixture.detectChanges();

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.titleTranslationParams).toEqual(params);
        });

        it('should pass content ref to modal', () => {
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

            const mockContent = {} as TemplateRef<any>;

            // viewChild() returns a callable signal; override it for this test
            (comp as any).content = () => mockContent;

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.contentRef).toBe(mockContent);
        });

        it('should emit onConfirm when modal is confirmed', async () => {
            mockModalRef.result = Promise.resolve();
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const confirmSpy = vi.spyOn(comp.onConfirm, 'emit');

            comp.onOpenConfirmationModal();
            await mockModalRef.result;

            expect(confirmSpy).toHaveBeenCalled();
        });

        it('should emit onCancel when modal is dismissed', async () => {
            mockModalRef.result = Promise.reject('dismissed');
            vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const cancelSpy = vi.spyOn(comp.onCancel, 'emit');

            comp.onOpenConfirmationModal();

            // Wait for the rejection to be handled
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(cancelSpy).toHaveBeenCalled();
        });
    });
});
