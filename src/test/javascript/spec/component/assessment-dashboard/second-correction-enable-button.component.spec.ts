import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { stub } from 'sinon';

import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('SecondCorrectionEnableButtonComponent', () => {
    let comp: SecondCorrectionEnableButtonComponent;
    let fixture: ComponentFixture<SecondCorrectionEnableButtonComponent>;
    const router = new MockRouter();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SecondCorrectionEnableButtonComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                JhiLanguageHelper,
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: Router, useValue: router },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SecondCorrectionEnableButtonComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('test call', () => {
        const stubEmit = stub(comp.ngModelChange, 'emit');
        comp.triggerSecondCorrectionButton();
        expect(stubEmit).to.have.been.called;
    });
});
