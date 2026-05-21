import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-test-component',
    template: '<button jhiDeleteButton [actionType]="actionType" entityTitle="title" deleteQuestion="question" deleteConfirmationText="text"></button>',
    imports: [DeleteButtonDirective],
})
class TestComponent {
    actionType = ActionType.Delete;
}

describe('DeleteDialogDirective', () => {
    let comp: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
    let deleteDialogService: DeleteDialogService;
    let translateService: TranslateService;
    let translateSpy: jest.SpyInstance;

    const mockDialogRef = {
        onClose: new Subject<void>(),
        close: jest.fn(),
    } as unknown as DynamicDialogRef;

    const mockDialogService = {
        open: jest.fn().mockReturnValue(mockDialogRef),
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
                translateSpy = jest.spyOn(translateService, 'instant');
            }),
    );

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('directive should be correctly initialized', () => {
        fixture.detectChanges();
        expect(translateSpy).toHaveBeenCalledOnce();
        expect(translateSpy).toHaveBeenCalledWith('entity.action.delete');

        // Check that button was assigned with proper classes and type.
        const deleteButton = debugElement.query(By.css('.btn.btn-danger.btn-sm.me-1'));
        expect(deleteButton).not.toBeNull();
        expect(deleteButton.properties['type']).toBe('submit');

        // Check that delete text span was added to the DOM.
        const deleteTextSpan = debugElement.query(By.css('.d-none.d-xl-inline'));
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

    it('on click should call delete dialog service', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();
        fixture.detectChanges();
        const deleteDialogSpy = jest.spyOn(deleteDialogService, 'openDeleteDialog');
        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        directiveEl.nativeElement.click();
        fixture.detectChanges();
        expect(deleteDialogSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

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
