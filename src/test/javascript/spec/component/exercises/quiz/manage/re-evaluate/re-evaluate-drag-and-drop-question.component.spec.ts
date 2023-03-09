import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../../test.module';

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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
