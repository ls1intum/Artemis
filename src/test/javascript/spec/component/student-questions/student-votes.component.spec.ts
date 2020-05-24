import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { StudentVotesComponent } from 'app/overview/student-questions/student-votes/student-votes.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

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
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }],
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
        componentFixture.detectChanges();
        component.votes = 42;
        componentFixture.detectChanges();
        component.userVote = null;
        componentFixture.detectChanges();
        // if not yet voted
        component.toggleUpVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(1);
        expect(component.userVote!.isPositive).to.be.true;
        // if already upvoted
        component.toggleUpVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(-1);
        expect(component.userVote).to.be.null;
        // if already downvoted
        component.userVote = { isPositive: false };
        componentFixture.detectChanges();
        component.toggleUpVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(2);
        expect(component.userVote!.isPositive).to.be.true;
    });

    it('should toggle downvote', () => {
        component.user = user1;
        componentFixture.detectChanges();
        component.votes = 42;
        componentFixture.detectChanges();
        component.userVote = null;
        componentFixture.detectChanges();
        // if not yet voted
        component.toggleDownVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(-1);
        expect(component.userVote!.isPositive).to.be.false;
        // if already downvoted
        component.toggleDownVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(+1);
        expect(component.userVote).to.be.null;
        // if already upvoted
        component.userVote = { isPositive: true };
        componentFixture.detectChanges();
        component.toggleDownVote();
        componentFixture.detectChanges();
        expect(component.voteValueChange).to.deep.equal(-1);
        expect(component.userVote!.isPositive).to.be.false;
    });
});
