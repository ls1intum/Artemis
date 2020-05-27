import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
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
            ],
        })
            .compileComponents()
            .then(() => {
                ratingComponentFixture = TestBed.createComponent(RatingComponent);
                ratingComponent = ratingComponentFixture.componentInstance;
                ratingService = TestBed.inject(RatingService);

                ratingComponent.result = { id: 89 } as Result;
                ratingComponent.result.submission = { id: 1 } as Submission;
            });
    });

    it('should get rating', () => {
        sinon.spy(ratingService, 'getRating');
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).to.have.been.calledOnce;
        expect(ratingComponent.result.id).to.equal(89);
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating = new Rating({ id: 89 } as Result, 0);
            sinon.spy(ratingService, 'createRating');
            sinon.spy(ratingService, 'updateRating');
        });

        it('should create new rating', () => {
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.createRating).to.have.been.calledOnce;
            expect(ratingService.updateRating).to.not.have.been.called;
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
        });
    });
});
