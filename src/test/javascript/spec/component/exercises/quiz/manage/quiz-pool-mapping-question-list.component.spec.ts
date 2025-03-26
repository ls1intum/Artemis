import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuizPoolMappingQuestionListComponent } from 'app/quiz/manage/quiz-pool-mapping-question-list.component';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
// Mock before import to prevent errors
jest.mock('@angular/cdk/drag-drop', () => {
    const originalModule = jest.requireActual('@angular/cdk/drag-drop');
    return {
        ...originalModule,
        moveItemInArray: jest.fn(),
        transferArrayItem: jest.fn(),
    };
});
import * as DragDrop from '@angular/cdk/drag-drop';
import { moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';

describe('QuizPoolMappingQuestionListComponent', () => {
    let fixture: ComponentFixture<QuizPoolMappingQuestionListComponent>;
    let component: QuizPoolMappingQuestionListComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DragDrop.DragDropModule],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizPoolMappingQuestionListComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });
    it('should move question in the same group', () => {
        const container = {
            data: {},
        };
        const event = {
            previousContainer: container,
            container: container,
            previousIndex: 0,
            currentIndex: 1,
        } as DragDrop.CdkDragDrop<QuizQuestion[]>;

        component.handleOnDropQuestion(event);

        expect(moveItemInArray).toHaveBeenCalledOnce();
    });

    it('should move question within different groups', () => {
        const container0 = {
            data: {},
        };
        const container1 = {
            data: {},
        };
        const event = {
            previousContainer: container0,
            container: container1,
            previousIndex: 0,
            currentIndex: 0,
        } as DragDrop.CdkDragDrop<QuizQuestion[]>;

        component.handleOnDropQuestion(event);

        expect(transferArrayItem).toHaveBeenCalledOnce();
    });
});
