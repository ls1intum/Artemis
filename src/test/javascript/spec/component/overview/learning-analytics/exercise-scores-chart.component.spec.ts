import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseScoresChartComponent } from 'app/overview/learning-analytics/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';
import { RouterTestingModule } from '@angular/router/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { LearningAnalyticsService } from 'app/overview/learning-analytics/learning-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseScoresChartComponent', () => {
    let fixture: ComponentFixture<ExerciseScoresChartComponent>;
    let component: ExerciseScoresChartComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ChartsModule, RouterTestingModule.withRoutes([])],
            declarations: [ExerciseScoresChartComponent, MockPipe(TranslatePipe)],
            providers: [
                MockProvider(JhiAlertService),
                MockProvider(TranslateService),
                MockProvider(LearningAnalyticsService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: 1,
                            }),
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseScoresChartComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
