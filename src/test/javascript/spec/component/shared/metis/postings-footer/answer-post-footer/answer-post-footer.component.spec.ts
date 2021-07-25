import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { SinonStub, spy, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostFooterComponent', () => {
    let component: AnswerPostFooterComponent;
    let fixture: ComponentFixture<AnswerPostFooterComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;

    const unApprovedAnswerPost = {
        id: 1,
        creationDate: moment(),
        content: 'not approved most recent',
        tutorApproved: false,
    } as AnswerPost;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [AnswerPostFooterComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostFooterComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = unApprovedAnswerPost;
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
