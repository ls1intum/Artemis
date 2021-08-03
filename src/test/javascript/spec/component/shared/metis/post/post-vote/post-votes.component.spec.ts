import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PostVotesComponent } from 'app/shared/metis/post/post-votes/post-votes.component';
import { MockSyncStorage } from '../../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostService } from 'app/shared/metis/post/post.service';
import { MockPostService } from '../../../../../helpers/mocks/service/mock-post.service';
import { Post } from 'app/entities/metis/post.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostVotesComponent', () => {
    let component: PostVotesComponent;
    let componentFixture: ComponentFixture<PostVotesComponent>;

    const user1 = {
        id: 1,
    } as User;

    const user2 = {
        id: 2,
    } as User;

    const post = {
        id: 1,
        author: user2,
        votes: 0,
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: MetisService, useClass: MetisService },
                { provide: PostService, useClass: MockPostService },
            ],
            declarations: [PostVotesComponent],
        })
            .overrideTemplate(PostVotesComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PostVotesComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should toggle upvote', () => {
        component.user = user1;
        component.post = post;
        component.userVote = null;
        // if not yet voted
        component.toggleUpVote();
        expect(component.voteValueChange).to.deep.equal(1);
        expect(component.userVote!.isPositive).to.be.true;
        // if already upvoted
        component.toggleUpVote();
        expect(component.voteValueChange).to.deep.equal(-1);
        expect(component.userVote).to.be.null;
        // if already downvoted
        component.userVote = { isPositive: false };
        component.toggleUpVote();
        expect(component.voteValueChange).to.deep.equal(2);
        expect(component.userVote!.isPositive).to.be.true;
    });

    it('should toggle downvote', () => {
        component.user = user1;
        component.post = post;
        component.userVote = null;
        // if not yet voted
        component.toggleDownVote();
        expect(component.voteValueChange).to.deep.equal(-1);
        expect(component.userVote!.isPositive).to.be.false;
        // if already downvoted
        component.toggleDownVote();
        expect(component.voteValueChange).to.deep.equal(+1);
        expect(component.userVote).to.be.null;
        // if already upvoted
        component.userVote = { isPositive: true };
        component.toggleDownVote();
        expect(component.voteValueChange).to.deep.equal(-2);
        expect(component.userVote!.isPositive).to.be.false;
    });
});
