import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { Result } from 'app/entities/result';
import { ArtemisSharedModule } from 'app/shared';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FileUploadSubmissionComponent } from 'app/file-upload-submission/file-upload-submission.component';
import { TranslateModule } from '@ngx-translate/core';
import { FileUploadResultComponent } from 'app/file-upload-submission/file-upload-result/file-upload-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Feedback } from 'app/entities/feedback';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadResultComponent;
    let fixture: ComponentFixture<FileUploadResultComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, ArtemisSharedModule, TranslateModule.forRoot(), ArtemisSharedComponentModule],
            declarations: [FileUploadResultComponent],
            providers: [
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadResultComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should show feedback items', () => {
        const f1 = new Feedback(-2, 'Example');
        const f2 = new Feedback(2, 'Example2');
        const generalFeedback = new Feedback(0, 'General');
        comp.result = <Result>{ feedbacks: [f1, f2, generalFeedback] };

        // check that feedback items are correctly split in the component
        expect(comp.feedbacks[0]).to.be.equal(f1);
        expect(comp.feedbacks[1]).to.be.equal(f2);
        expect(comp.generalFeedback).to.be.equal(generalFeedback);

        fixture.detectChanges();

        const groupedFeedbackElements = debugElement.query(By.css('div'));
        expect(groupedFeedbackElements).to.exist;
        // exactly two feedback items are displayed
        expect(groupedFeedbackElements.childNodes.length).to.be.equal(2);
    });
});
