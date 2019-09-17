import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { JhiAlertErrorComponent } from 'app/shared';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { By } from '@angular/platform-browser';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiAlertService, NgJhipsterModule } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

chai.use(sinonChai);
const expect = chai.expect;

describe('DeleteDialogComponent', () => {
    let comp: DeleteDialogComponent;
    let fixture: ComponentFixture<DeleteDialogComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FormsModule, NgJhipsterModule, NgbModule],
            declarations: [DeleteDialogComponent, JhiAlertErrorComponent],
            providers: [JhiLanguageHelper, JhiAlertService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DeleteDialogComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });
    it('Dialog is correctly initialized', fakeAsync(() => {
        const modalTitle = fixture.debugElement.query(By.css('.modal-title'));
        expect(modalTitle).to.exist;
        comp.entityTitle = 'title';
        comp.deleteQuestion = 'artemisApp.exercise.delete.question';
        comp.deleteConfirmationText = 'artemisApp.exercise.delete.typeNameToConfirm';
        fixture.detectChanges();
    }));
});
