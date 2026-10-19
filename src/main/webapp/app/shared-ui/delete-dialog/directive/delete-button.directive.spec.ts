import { vi } from 'vitest';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { WritableSignal, inputBinding, signal } from '@angular/core';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';

describe('DeleteDialogDirective', () => {
    let fixture: DirectiveFixture<DeleteButtonDirective>;
    let actionType: WritableSignal<ActionType>;
    let renderStyle: WritableSignal<boolean>;
    let deleteDialogService: DeleteDialogService;
    let translateService: TranslateService;
    let translateSpy: ReturnType<typeof vi.spyOn>;

    const mockDialogRef = {
        onClose: new Subject<void>(),
        close: vi.fn(),
    } as unknown as DynamicDialogRef;

    const mockDialogService = {
        open: vi.fn().mockReturnValue(mockDialogRef),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: mockDialogService },
            ],
        });
        actionType = signal(ActionType.Delete);
        renderStyle = signal(true);
        fixture = TestBed.createDirective(DeleteButtonDirective, {
            tagName: 'button',
            bindings: [
                inputBinding('renderButtonStyle', renderStyle),
                inputBinding('actionType', actionType),
                inputBinding('entityTitle', () => 'title'),
                inputBinding('deleteQuestion', () => 'question'),
                inputBinding('deleteConfirmationText', () => 'text'),
            ],
        });
        deleteDialogService = TestBed.inject(DeleteDialogService);
        translateService = TestBed.inject(TranslateService);
        translateSpy = vi.spyOn(translateService, 'instant');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('directive should be correctly initialized', () => {
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.delete');

        // Check that button was assigned with proper classes and type.
        const deleteButton = fixture.debugElement;
        expect(deleteButton.nativeElement.matches('.btn.btn-danger.btn-sm.me-1')).toBe(true);
        expect(deleteButton.properties['type']).toBe('submit');

        // Check that delete text span was added to the DOM.
        const deleteTextSpan = deleteButton.nativeElement.querySelector('.d-none.d-xl-inline');
        expect(deleteTextSpan).not.toBeNull();
        expect(deleteTextSpan.textContent).not.toBeNull();

        const directiveInstance = fixture.directiveInstance;
        // Signal inputs need to be called as functions
        expect(directiveInstance.entityTitle()).toBe('title');
        expect(directiveInstance.deleteQuestion()).toBe('question');
        expect(directiveInstance.deleteConfirmationText()).toBe('text');
    });

    it('should give the PrimeNG-styled (icon-only) button a translated aria-label and no Bootstrap span/classes', () => {
        renderStyle.set(false);
        fixture.detectChanges();

        const button = fixture.nativeElement;
        expect(button.querySelector('.d-none.d-xl-inline')).toBeNull();
        expect(button.classList.contains('btn')).toBe(false);
        expect(button.querySelector('.btn')).toBeNull();

        // The icon-only path has no visible text, so the directive must supply an accessible name.
        expect(button.getAttribute('aria-label')).toContain('entity.action.delete');
    });

    it('on click should call delete dialog service', () => {
        // Ignore console errors
        console.error = vi.fn();
        fixture.detectChanges();
        const deleteDialogSpy = vi.spyOn(deleteDialogService, 'openDeleteDialog');
        (fixture.nativeElement as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(deleteDialogSpy).toHaveBeenCalledOnce();
    });

    it('action type cleanup should change button title', () => {
        actionType.set(ActionType.Cleanup);
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.cleanup');
    });

    it('action type reset should change button title', () => {
        actionType.set(ActionType.Reset);
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.reset');
    });
});
