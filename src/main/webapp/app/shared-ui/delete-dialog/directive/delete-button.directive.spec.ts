import { vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-test-component',
    template:
        '<button jhiDeleteButton [actionType]="actionType" [renderButtonText]="renderButtonText" entityTitle="title" deleteQuestion="question" deleteConfirmationText="text"></button>',
    imports: [DeleteButtonDirective],
})
class TestComponent {
    actionType = ActionType.Delete;
    renderButtonText = true;
}

describe('DeleteDialogDirective', () => {
    setupTestBed({ zoneless: true });
    let comp: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
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

    beforeEach(() =>
        TestBed.configureTestingModule({
            imports: [TestComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: mockDialogService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                deleteDialogService = TestBed.inject(DeleteDialogService);
                translateService = TestBed.inject(TranslateService);
                translateSpy = vi.spyOn(translateService, 'instant');
            }),
    );

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('directive should be correctly initialized', () => {
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.delete');

        // Styled as a PrimeNG danger button matching the pButton View/Edit siblings: normal padding, NOT
        // icon-only (the label is present, only hidden below md).
        const deleteButton = debugElement.query(By.css('.p-button.p-component.p-button-sm.p-button-danger'));
        expect(deleteButton).not.toBeNull();
        expect(deleteButton.nativeElement.classList.contains('p-button-icon-only')).toBe(false);
        expect(deleteButton.properties['type']).toBe('submit');

        // The label span carries p-button-label and collapses below md (shown from md upwards), like sibling pButton labels.
        const deleteTextSpan = debugElement.query(By.css('.p-button-label.hidden.md\\:inline'));
        expect(deleteTextSpan).not.toBeNull();
        expect(deleteTextSpan.nativeElement.textContent).not.toBeNull();

        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        expect(directiveEl).not.toBeNull();
        const directiveInstance = directiveEl.injector.get(DeleteButtonDirective);
        // Signal inputs need to be called as functions
        expect(directiveInstance.entityTitle()).toBe('title');
        expect(directiveInstance.deleteQuestion()).toBe('question');
        expect(directiveInstance.deleteConfirmationText()).toBe('text');
    });

    it('renders icon-only with no label span when renderButtonText is false', () => {
        comp.renderButtonText = false;
        fixture.detectChanges();
        const iconOnly = debugElement.query(By.css('.p-button.p-component.p-button-sm.p-button-danger.p-button-icon-only'));
        expect(iconOnly).not.toBeNull();
        expect(debugElement.query(By.css('.p-button-label'))).toBeNull();
    });

    it('on click should call delete dialog service', () => {
        // Ignore console errors
        console.error = vi.fn();
        fixture.detectChanges();
        const deleteDialogSpy = vi.spyOn(deleteDialogService, 'openDeleteDialog');
        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        directiveEl.nativeElement.click();
        fixture.detectChanges();
        expect(deleteDialogSpy).toHaveBeenCalledOnce();
    });

    it('action type cleanup should change button title', () => {
        comp.actionType = ActionType.Cleanup;
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.cleanup');
    });

    it('action type reset should change button title', () => {
        comp.actionType = ActionType.Reset;
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.reset');
    });
});
