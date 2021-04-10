import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import { JhiAlertService } from 'ng-jhipster';
import { MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import 'chart.js';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExerciseScoresChartService } from 'app/overview/visualizations/exercise-scores-chart.service';
import { RouterTestingModule } from '@angular/router/testing';

chai.use(sinonChai);
const expect = chai.expect;

class MockActivatedRoute {
    parent: any;
    params: any;

    constructor(options: { parent?: any; params?: any }) {
        this.parent = options.parent;
        this.params = options.params;
    }
}

const mockActivatedRoute = new MockActivatedRoute({
    parent: new MockActivatedRoute({
        params: of({ courseId: '1' }),
    }),
});

describe('ExerciseScoresChartComponent', () => {
    let fixture: ComponentFixture<ExerciseScoresChartComponent>;
    let component: ExerciseScoresChartComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ChartsModule, RouterTestingModule.withRoutes([])],
            declarations: [ExerciseScoresChartComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(JhiAlertService),
                MockProvider(TranslateService),
                MockProvider(ExerciseScoresChartService),

                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
            ],
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
        expect(component.courseId).to.equal(1);
    });
});
