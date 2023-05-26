import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../../test.module';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';

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

    it('should return files', () => {
        fixture.detectChanges();
        component.dragAndDropQuestionEditComponent = {
            newDragItemFiles: new Map<string, File>([
                ['path/to/dIFile/test1.jpg', new File([], 'test1.jpg')],
                ['path/to/dIFile/test2.jpg', new File([], 'test2.jpg')],
            ]),
            question: { backgroundFilePath: 'path/to/bGfile/test3.png' },
            backgroundFile: new File([], 'test3.png'),
        } as DragAndDropQuestionEditComponent;
        expect(component.getFiles()).toEqual(
            new Map<string, File>([
                ['path/to/dIFile/test1.jpg', new File([], 'test1.jpg')],
                ['path/to/dIFile/test2.jpg', new File([], 'test2.jpg')],
                ['path/to/bGfile/test3.png', new File([], 'test3.png')],
            ]),
        );
    });
});
