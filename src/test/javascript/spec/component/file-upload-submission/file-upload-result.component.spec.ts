import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { Result } from 'app/entities/result.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { FileUploadResultComponent } from 'app/exercises/file-upload/participate/file-upload-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Feedback } from 'app/entities/feedback.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadResultComponent', () => {
    let comp: FileUploadResultComponent;
    let fixture: ComponentFixture<FileUploadResultComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, ArtemisSharedModule, TranslateModule.forRoot(), ArtemisSharedComponentModule],
            declarations: [FileUploadResultComponent],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
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
        const f1 = new Feedback();
        f1.credits = -2;
        f1.text = 'Example';
        const f2 = new Feedback();
        f2.credits = 2;
        f2.text = 'Example2';
        comp.result = <Result>{ feedbacks: [f1, f2] };

        // check that feedback items are correctly split in the component
        expect(comp.feedbacks[0]).to.be.equal(f1);
        expect(comp.feedbacks[1]).to.be.equal(f2);

        fixture.detectChanges();

        const groupedFeedbackElements = debugElement.query(By.css('div'));
        expect(groupedFeedbackElements).to.exist;

        // exactly two feedback items are displayed
        expect(groupedFeedbackElements.childNodes.length).to.be.equal(2);
    });
});
