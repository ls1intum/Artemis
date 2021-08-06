import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostReactionsBarComponent', () => {
    let component: PostReactionsBarComponent;
    let fixture: ComponentFixture<PostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;

    const reaction = {
        id: 1,
        emojiId: 'smile',
    } as Reaction;

    const post = {
        id: 1,
        content: 'post with reaction',
        reactions: [reaction],
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [OverlayModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostReactionsBarComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostReactionsBarComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = post;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and reactions correctly', () => {
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).to.deep.equal(false);
        fixture.detectChanges();
        const reactions = getElement(debugElement, 'emoji-mart');
        expect(reactions).to.exist;
        expect(component.reactionCountMap).to.have.length(1);
    });
});
