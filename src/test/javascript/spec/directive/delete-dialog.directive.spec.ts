import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../test.module';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { TranslatePipeMock } from '../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockComponent, MockDirective } from 'ng-mocks';

@Component({
    selector: 'jhi-test-component',
    template: '<button jhiDeleteButton [actionType]="actionType" entityTitle="title" deleteQuestion="question" deleteConfirmationText="text"></button>',
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

    beforeEach(() =>
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, NgbModule],
            declarations: [TestComponent, DeleteButtonDirective, MockComponent(DeleteDialogComponent), TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [JhiLanguageHelper, AlertService],
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
        expect(deleteButton).not.toBe(null);
        expect(deleteButton.properties['type']).toBe('submit');

        // Check that delete text span was added to the DOM.
        const deleteTextSpan = debugElement.query(By.css('.d-none.d-md-inline'));
        expect(deleteTextSpan).not.toBe(null);
        expect(deleteTextSpan.nativeElement.textContent).not.toBe(null);

        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        expect(directiveEl).not.toBe(null);
        const directiveInstance = directiveEl.injector.get(DeleteButtonDirective);
        expect(directiveInstance.entityTitle).toBe('title');
        expect(directiveInstance.deleteQuestion).toBe('question');
        expect(directiveInstance.deleteConfirmationText).toBe('text');
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
