import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import { MockComponent, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../../../../test.module';
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ReEvaluateShortAnswerQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateShortAnswerQuestionComponent>;
    let component: ReEvaluateShortAnswerQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ReEvaluateShortAnswerQuestionComponent, MockComponent(ShortAnswerQuestionEditComponent)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ReEvaluateShortAnswerQuestionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
