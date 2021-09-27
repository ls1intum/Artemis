import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { UserService } from 'app/core/user/user.service';
import { MockUserService } from '../../helpers/mocks/service/mock-user.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { RatingModule as StarratingModule, StarRatingComponent } from 'ng-starrating';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { MockRatingService } from '../../helpers/mocks/service/mock-rating.service';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { Rating } from 'app/entities/rating.model';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { Participation } from 'app/entities/participation/participation.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('RatingComponent', () => {
    let ratingComponent: RatingComponent;
    let ratingComponentFixture: ComponentFixture<RatingComponent>;
    let ratingService: RatingService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, StarratingModule],
            declarations: [RatingComponent],
            providers: [
                { provide: RatingService, useClass: MockRatingService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: UserService, useClass: MockUserService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                ratingComponentFixture = TestBed.createComponent(RatingComponent);
                ratingComponent = ratingComponentFixture.componentInstance;
                ratingService = TestBed.inject(RatingService);

                ratingComponent.result = { id: 89 } as Result;
                ratingComponent.result.submission = { id: 1 } as Submission;
                ratingComponent.result.participation = { id: 1 } as Participation;
            });
    });

    it('should get rating', () => {
        sinon.spy(ratingService, 'getRating');
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).to.have.been.calledOnce;
        expect(ratingComponent.result?.id).to.equal(89);
    });

    it('should return due to missing result', () => {
        sinon.spy(ratingService, 'getRating');
        delete ratingComponent.result;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).to.not.have.been.called;
    });

    it('should return due to missing participation', () => {
        sinon.spy(ratingService, 'getRating');
        delete ratingComponent.result?.participation;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).to.not.have.been.called;
    });

    it('should create new local rating', () => {
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating.result?.id).to.be.equal(89);
        expect(ratingComponent.rating.rating).to.be.equal(0);
    });

    it('should set rating received from server', () => {
        const fake = sinon.fake.returns(of(new Rating({ id: 90 } as Result, 1)));
        sinon.replace(ratingService, 'getRating', fake);
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating.result?.id).to.be.equal(90);
        expect(ratingComponent.rating.rating).to.be.equal(1);
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating = new Rating({ id: 89 } as Result, 0);
            sinon.spy(ratingService, 'createRating');
            sinon.spy(ratingService, 'updateRating');
        });

        it('should return', () => {
            ratingComponent.disableRating = true;
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.createRating).to.not.have.been.called;
            expect(ratingService.updateRating).to.not.have.been.called;
        });

        it('should create new rating', () => {
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.createRating).to.have.been.calledOnce;
            expect(ratingService.updateRating).to.not.have.been.called;
            expect(ratingComponent.rating.result?.id).to.be.equal(89);
            expect(ratingComponent.rating.rating).to.be.equal(2);
        });

        it('should update rating', () => {
            ratingComponent.rating.id = 89;
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.updateRating).to.have.been.calledOnce;
            expect(ratingService.createRating).to.not.have.been.called;
            expect(ratingComponent.rating.result?.id).to.be.equal(89);
            expect(ratingComponent.rating.rating).to.be.equal(2);
        });
    });
});
