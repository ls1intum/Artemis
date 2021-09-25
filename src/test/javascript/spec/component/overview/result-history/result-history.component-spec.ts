import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Result } from 'app/entities/result.model';
import sinonChai from 'sinon-chai';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { MockModule } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
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
            imports: [MockModule(ArtemisSharedModule)],
            declarations: [ResultHistoryComponent],
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
