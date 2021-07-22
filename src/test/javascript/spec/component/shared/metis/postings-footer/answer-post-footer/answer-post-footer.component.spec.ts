import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

let metisService: MetisService;
let metisServiceUserAuthorityStub: SinonStub;

describe('AnswerPostFooterComponent', () => {
    let component: AnswerPostFooterComponent;
    let fixture: ComponentFixture<AnswerPostFooterComponent>;

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
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and answer post footer correctly', () => {
        component.posting = unApprovedAnswerPost;
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(false);
        fixture.detectChanges();
        const approvedBadge = fixture.debugElement.nativeElement.querySelector('.approved-badge');
        expect(approvedBadge).to.not.exist;
    });

    it('should initialize user authority and answer post footer correctly', () => {
        component.posting = unApprovedAnswerPost;
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(true);
        fixture.detectChanges();
        const approvedBadge = fixture.debugElement.nativeElement.querySelector('.approved-badge');
        expect(approvedBadge).to.not.exist;
    });

    it('should toggle answer post from unapproved to approved on click', () => {
        const toggleApproveSpy = sinon.spy(component, 'toggleApprove');
        component.posting = unApprovedAnswerPost;
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        fixture.detectChanges();
        const toggleElement = fixture.debugElement.nativeElement.querySelector('#toggleElement');
        toggleElement.click();
        fixture.detectChanges();
        expect(toggleApproveSpy).to.have.been.called;
        expect(component.posting.tutorApproved).to.be.equal(true);
        const approvedBadge = fixture.debugElement.nativeElement.querySelector('.approved-badge');
        expect(approvedBadge).to.exist;
    });
});
