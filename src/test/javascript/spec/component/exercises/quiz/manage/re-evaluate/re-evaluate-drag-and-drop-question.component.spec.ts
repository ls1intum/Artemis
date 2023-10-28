import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../../test.module';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/exercises/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';

describe('ReEvaluateDragAndDropQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateDragAndDropQuestionComponent>;
    let component: ReEvaluateDragAndDropQuestionComponent;

    const fileName1 = 'test1.jpg';
    const file1 = new File([], fileName1);
    const fileName2 = 'test2.jpg';
    const file2 = new File([], fileName2);
    const fileName3 = 'test3.png';
    const file3 = new File([], fileName3);

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

    it('should add file', () => {
        const path = 'this/is/a/path/to/a/file.png';
        component.handleAddFile({ fileName: fileName1, file: file1 });
        component.handleAddFile({ fileName: fileName2, file: file2, path });

        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName2, { file: file2, path }],
            ]),
        );
    });

    it('should remove file', () => {
        component.fileMap = new Map<string, { file: File; path?: string }>([
            [fileName1, { file: file1 }],
            [fileName2, { file: file2 }],
            [fileName3, { file: file3 }],
        ]);
        component.handleRemoveFile(fileName2);
        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName3, { file: file3 }],
            ]),
        );
    });
});
