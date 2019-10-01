import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement, Component } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { JhiAlertErrorComponent } from 'app/shared';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiAlertService, NgJhipsterModule } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import * as sinon from 'sinon';
import { DeleteDialogDirective } from 'app/shared/delete-dialog/delete-dialog.directive';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    selector: 'jhi-test-component',
    template:
        '<button jhiDeleteDialog entityTitle="title" deleteQuestion="question" deleteConfirmationText="text" (delete)="delete($event)" checkboxText="checkbox" additionalCheckboxText="additional"></button>',
})
class TestComponent {
    eventCalled: { checkboxValue: boolean; additionalCheckboxValue: boolean };
    delete($event: { checkboxValue: boolean; additionalCheckboxValue: boolean }) {
        this.eventCalled = $event;
    }
}

describe('DeleteDialogDirective', () => {
    let comp: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let debugElement: DebugElement;
    let deleteDialogService: DeleteDialogService;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FormsModule, NgJhipsterModule, NgbModule],
            declarations: [TestComponent, DeleteDialogDirective, DeleteDialogComponent, JhiAlertErrorComponent],
            providers: [JhiLanguageHelper, JhiAlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                deleteDialogService = TestBed.get(DeleteDialogService);
            });
    });

    it('directive should correctly initialized', fakeAsync(() => {
        fixture.detectChanges();
        const directiveEl = fixture.debugElement.query(By.directive(DeleteDialogDirective));
        expect(directiveEl).to.be.not.null;

        const directiveInstance = directiveEl.injector.get(DeleteDialogDirective);
        expect(directiveInstance.additionalCheckboxText).to.be.equal('additional');
        expect(directiveInstance.entityTitle).to.be.equal('title');
        expect(directiveInstance.deleteQuestion).to.be.equal('question');
        expect(directiveInstance.deleteConfirmationText).to.be.equal('deleteConfirmationText');
        expect(directiveInstance.checkboxText).to.be.equal('checkbox');
    }));

    it('on click should call delete dialog service', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();
        fixture.detectChanges();
        const deleteDialogSpy = sinon.spy(deleteDialogService, 'openDeleteDialog');
        const directiveEl = fixture.debugElement.query(By.directive(DeleteDialogDirective));
        directiveEl.nativeElement.click();
        fixture.detectChanges();
        expect(deleteDialogSpy.callCount).to.equal(1);
    }));
});
