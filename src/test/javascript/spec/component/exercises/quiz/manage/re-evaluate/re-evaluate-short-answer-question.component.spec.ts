import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ReEvaluateShortAnswerQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/short-answer-question/re-evaluate-short-answer-question.component';
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../../test.module';

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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
