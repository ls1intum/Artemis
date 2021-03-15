import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import { MockComponent, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../../../../test.module';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ReEvaluateDragAndDropQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateDragAndDropQuestionComponent>;
    let component: ReEvaluateDragAndDropQuestionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ReEvaluateDragAndDropQuestionComponent, MockComponent(DragAndDropQuestionEditComponent)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ReEvaluateDragAndDropQuestionComponent);
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
