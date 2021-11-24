import { ComponentFixture, fakeAsync, discardPeriodicTasks, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Component, DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../test.module';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import * as sinon from 'sinon';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { TranslatePipeMock } from '../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), FormsModule, NgbModule],
            declarations: [TestComponent, DeleteButtonDirective, DeleteDialogComponent, AlertComponent, TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [JhiLanguageHelper, AlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                deleteDialogService = TestBed.inject(DeleteDialogService);
                translateService = TestBed.inject(TranslateService);
            });
    });

    it('directive should be correctly initialized', fakeAsync(() => {
        const translateSpy = sinon.spy(translateService, 'instant');

        fixture.detectChanges();
        expect(translateSpy.callCount).to.be.equal(1);
        expect(translateSpy.lastCall.lastArg).to.be.equal('entity.action.delete');

        // Check that button was assigned with proper classes and type.
        const deleteButton = debugElement.query(By.css('.btn.btn-danger.btn-sm.me-1'));
        expect(deleteButton).to.exist;
        expect(deleteButton.properties['type']).to.be.equal('submit');

        // Check that delete text span was added to the DOM.
        const deleteTextSpan = debugElement.query(By.css('.d-none.d-md-inline'));
        expect(deleteTextSpan).to.exist;
        expect(deleteTextSpan.nativeElement.textContent).to.not.be.null;

        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        expect(directiveEl).to.be.not.null;
        const directiveInstance = directiveEl.injector.get(DeleteButtonDirective);
        expect(directiveInstance.entityTitle).to.be.equal('title');
        expect(directiveInstance.deleteQuestion).to.be.equal('question');
        expect(directiveInstance.deleteConfirmationText).to.be.equal('text');
    }));

    it('on click should call delete dialog service', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();
        fixture.detectChanges();
        const deleteDialogSpy = sinon.spy(deleteDialogService, 'openDeleteDialog');
        const directiveEl = debugElement.query(By.directive(DeleteButtonDirective));
        directiveEl.nativeElement.click();
        fixture.detectChanges();
        expect(deleteDialogSpy.callCount).to.equal(1);
        discardPeriodicTasks();
    }));

    it('action type cleanup should change button title', fakeAsync(() => {
        const translateSpy = sinon.spy(translateService, 'instant');
        comp.actionType = ActionType.Cleanup;
        fixture.detectChanges();
        expect(translateSpy.callCount).to.be.equal(1);
        expect(translateSpy.lastCall.lastArg).to.be.equal('entity.action.cleanup');
    }));

    it('action type reset should change button title', fakeAsync(() => {
        const translateSpy = sinon.spy(translateService, 'instant');
        comp.actionType = ActionType.Reset;
        fixture.detectChanges();
        expect(translateSpy.callCount).to.be.equal(1);
        expect(translateSpy.lastCall.lastArg).to.be.equal('entity.action.reset');
    }));
});
