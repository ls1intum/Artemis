import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostReactionsBarComponent', () => {
    let component: AnswerPostReactionsBarComponent;
    let fixture: ComponentFixture<AnswerPostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;

    const answerPost = {
        id: 1,
        content: 'post without reaction',
    } as AnswerPost;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [OverlayModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [AnswerPostReactionsBarComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostReactionsBarComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = answerPost;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and reactions correctly', () => {
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).to.deep.equal(true);
        fixture.detectChanges();
        const reaction = getElement(debugElement, 'emoji-mart');
        expect(reaction).to.not.exist;
        expect(component.reactionCountMap).to.deep.equal({});
    });
});
