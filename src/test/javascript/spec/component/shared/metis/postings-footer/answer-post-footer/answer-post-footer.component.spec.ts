import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import * as sinon from 'sinon';
import { SinonStub, spy, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { metisAnswerPostUser1 } from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostFooterComponent', () => {
    let component: AnswerPostFooterComponent;
    let fixture: ComponentFixture<AnswerPostFooterComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [AnswerPostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(AnswerPostReactionsBarComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostFooterComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = metisAnswerPostUser1;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and answer post footer correctly', () => {
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(false);
        fixture.detectChanges();
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).to.not.exist;
    });

    it('should initialize user authority and answer post footer correctly', () => {
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(true);
        fixture.detectChanges();
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).to.not.exist;
    });

    it('should toggle answer post from unapproved to approved on click', () => {
        const toggleApproveSpy = spy(component, 'toggleApprove');
        metisServiceUserAuthorityStub.returns(true);
        fixture.detectChanges();
        const toggleElement = getElement(debugElement, '#toggleElement');
        toggleElement.click();
        fixture.detectChanges();
        expect(toggleApproveSpy).to.have.been.called;
        expect(component.posting.tutorApproved).to.be.equal(true);
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).to.exist;
    });
});
