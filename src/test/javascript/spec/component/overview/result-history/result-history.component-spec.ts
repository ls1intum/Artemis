import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Result } from 'app/entities/result.model';
import * as sinonChai from 'sinon-chai';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import * as sinon from 'sinon';
import * as chai from 'chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;
    let result: Result;

    beforeEach(() => {
        result = new Result();

        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisSharedModule), MockModule(MomentModule), MockModule(ArtemisProgrammingExerciseActionsModule), MockModule(ArtemisSharedCommonModule)],
            declarations: [
                ResultHistoryComponent,
                MockComponent(ResultComponent),
                MockComponent(UpdatingResultComponent),
                MockComponent(ResultDetailComponent),
                MockComponent(ResultHistoryComponent),
                MockComponent(SubmissionResultStatusComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultHistoryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should return the right values for result score', () => {
        fixture.detectChanges();
        result.score = 85;
        expect(component.resultIcon(result)).to.equal('check');
        expect(component.resultClass(result)).to.equal('success');

        result.score = 50;
        expect(component.resultIcon(result)).to.equal('times');
        expect(component.resultClass(result)).to.equal('warning');

        result.score = 30;
        expect(component.resultClass(result)).to.equal('danger');
    });

    it('should test absolute result', () => {
        fixture.detectChanges();

        expect(component.absoluteResult(result)).to.equal(0);

        result.resultString = 'failed';
        expect(component.absoluteResult(result)).to.equal(null);

        result.resultString = 'passed';
        expect(component.absoluteResult(result)).to.equal(null);

        result.resultString = 'no_right_value';
        expect(component.absoluteResult(result)).to.equal(0);

        result.resultString = '100 points';
        expect(component.absoluteResult(result)).to.equal(100);
    });
});
