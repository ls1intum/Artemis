import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { By } from '@angular/platform-browser';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import * as moment from 'moment';
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

    it('should initialize unapproved answer post footer correctly', () => {
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(false);
    });

    it('should initialize approved answer post footer correctly', () => {
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(true);
    });

    it('should toggle answer post from approved to unapproved', () => {
        component.posting = unApprovedAnswerPost;
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(false);
        fixture.detectChanges();
        const notApprovedBadge = fixture.debugElement.query(By.css('.not-approved-badge'));
        const approveBadge = fixture.debugElement.query(By.css('.approved-badge'));
        // Todo: finish test
    });
});
