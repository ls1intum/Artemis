import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { QuizPoolMappingQuestionListComponent } from 'app/exercises/quiz/manage/quiz-pool-mapping-question-list.component';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
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

describe('QuizPoolMappingQuestionListComponent', () => {
    let fixture: ComponentFixture<QuizPoolMappingQuestionListComponent>;
    let component: QuizPoolMappingQuestionListComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DragDrop.DragDropModule, HttpClientTestingModule],
            declarations: [QuizPoolMappingQuestionListComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockDirective(TranslateDirective)],
            providers: [],
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
