import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { StudentVotesComponent } from 'app/overview/student-questions/student-votes/student-votes.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentVotesComponent', () => {
    let component: StudentVotesComponent;
    let componentFixture: ComponentFixture<StudentVotesComponent>;

    const user1 = {
        id: 1,
    } as User;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
            ],
            declarations: [StudentVotesComponent],
        })
            .overrideTemplate(StudentVotesComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StudentVotesComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should toggle upvote', () => {
        component.user = user1;
        component.votes = 42;
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
        component.votes = 42;
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
