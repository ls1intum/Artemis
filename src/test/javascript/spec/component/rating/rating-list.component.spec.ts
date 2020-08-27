import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisTestModule } from '../../test.module';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { ActivatedRoute } from '@angular/router';
import { DifferencePipe } from 'ngx-moment';
import { of } from 'rxjs';

describe('RatingListComponent', () => {
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;

    const ratings = [{ id: 1 }, { id: 2 }, { id: 3 }];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisSharedModule, TranslateModule.forRoot(), ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [RatingListComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 123 }),
                },
                DifferencePipe,
                {
                    provide: RatingService,
                    useValue: {
                        getRatingsForDashboard() {
                            return of(ratings);
                        },
                    },
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RatingListComponent);
                component = fixture.componentInstance;

                component.ngOnInit();
            });
    });

    it('should initialize ratings', () => {
        expect(component.ratings.length).toEqual(3);
    });
});
