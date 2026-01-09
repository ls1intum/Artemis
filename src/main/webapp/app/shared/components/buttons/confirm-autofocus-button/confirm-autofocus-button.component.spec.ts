import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faCheck } from '@fortawesome/free-solid-svg-icons';

describe('ConfirmAutofocusButtonComponent', () => {
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
            expect(comp.disabled).toBeFalse();
        });

        it('should have default isLoading as false', () => {
            expect(comp.isLoading).toBeFalse();
        });

        it('should have default btnType as PRIMARY', () => {
            expect(comp.btnType).toBe(ButtonType.PRIMARY);
        });
    });

    describe('inputs', () => {
        it('should accept icon input', () => {
            comp.icon = faCheck;
            expect(comp.icon).toEqual(faCheck);
        });

        it('should accept title input', () => {
            comp.title = 'Test Title';
            expect(comp.title).toBe('Test Title');
        });

        it('should accept tooltip input', () => {
            comp.tooltip = 'Test Tooltip';
            expect(comp.tooltip).toBe('Test Tooltip');
        });

        it('should accept confirmationTitle input', () => {
            comp.confirmationTitle = 'Confirm Title';
            expect(comp.confirmationTitle).toBe('Confirm Title');
        });

        it('should accept confirmationText input', () => {
            comp.confirmationText = 'Are you sure?';
            expect(comp.confirmationText).toBe('Are you sure?');
        });

        it('should accept confirmationTitleTranslationParams input', () => {
            const params = { name: 'Test' };
            comp.confirmationTitleTranslationParams = params;
            expect(comp.confirmationTitleTranslationParams).toEqual(params);
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
            const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            comp.confirmationText = 'Plain text content';
            comp.confirmationTitle = 'Test Title';
            comp.textIsMarkdown = false;

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(mockModalRef.componentInstance!.text).toBe('Plain text content');
            expect(mockModalRef.componentInstance!.textIsMarkdown).toBeFalse();
            expect(mockModalRef.componentInstance!.title).toBe('Test Title');
        });

        it('should open modal with markdown text', async () => {
            const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            comp.confirmationText = '**Bold text**';
            comp.confirmationTitle = 'Test Title';
            comp.textIsMarkdown = true;

            comp.onOpenConfirmationModal();

            expect(openSpy).toHaveBeenCalledOnce();
            expect(mockModalRef.componentInstance!.textIsMarkdown).toBeTrue();
        });

        it('should set translateText to true when specified', () => {
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            comp.translateText = true;

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.translateText).toBeTrue();
        });

        it('should set translateText to false when not specified', () => {
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            comp.translateText = undefined;

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.translateText).toBeFalse();
        });

        it('should pass titleTranslationParams to modal', () => {
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const params = { name: 'Test' };
            comp.confirmationTitleTranslationParams = params;

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.titleTranslationParams).toEqual(params);
        });

        it('should pass content ref to modal', () => {
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const mockContent = {} as any;
            comp.content = mockContent;

            comp.onOpenConfirmationModal();

            expect(mockModalRef.componentInstance!.contentRef).toEqual(mockContent);
        });

        it('should emit onConfirm when modal is confirmed', async () => {
            mockModalRef.result = Promise.resolve();
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const confirmSpy = jest.spyOn(comp.onConfirm, 'emit');

            comp.onOpenConfirmationModal();
            await mockModalRef.result;

            expect(confirmSpy).toHaveBeenCalledWith(undefined);
        });

        it('should emit onCancel when modal is dismissed', async () => {
            mockModalRef.result = Promise.reject('dismissed');
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            const cancelSpy = jest.spyOn(comp.onCancel, 'emit');

            comp.onOpenConfirmationModal();

            // Wait for the rejection to be handled
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(cancelSpy).toHaveBeenCalledWith(undefined);
        });
    });
});
