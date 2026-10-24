import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it } from 'vitest';

import { QuizQuestionPreviewComponent } from 'app/quiz/manage/quiz-question-preview/quiz-question-preview.component';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { QuizScoringInfoStudentModalComponent } from 'app/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizQuestionPreviewComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPreviewComponent>;

    const renderedQuestionTags = () => {
        const tags = ['jhi-multiple-choice-question', 'jhi-drag-and-drop-question', 'jhi-short-answer-question'];
        return tags.filter((tag) => fixture.debugElement.query(By.css(tag)) !== null);
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizQuestionPreviewComponent],
        })
            // The question components render markdown and images and need their own providers; this spec only checks which one is chosen.
            .overrideComponent(QuizQuestionPreviewComponent, {
                remove: { imports: [MultipleChoiceQuestionComponent, DragAndDropQuestionComponent, ShortAnswerQuestionComponent] },
                add: { imports: [MockComponent(MultipleChoiceQuestionComponent), MockComponent(DragAndDropQuestionComponent), MockComponent(ShortAnswerQuestionComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(QuizQuestionPreviewComponent);
    });

    it.each([
        ['multiple choice', () => new MultipleChoiceQuestion(), 'jhi-multiple-choice-question'],
        ['drag and drop', () => new DragAndDropQuestion(), 'jhi-drag-and-drop-question'],
        ['short answer', () => new ShortAnswerQuestion(), 'jhi-short-answer-question'],
    ])('should render exactly the component of a %s question', (_name: string, createQuestion: () => QuizQuestion, expectedTag: string) => {
        fixture.componentRef.setInput('question', createQuestion());
        fixture.detectChanges();

        expect(renderedQuestionTags()).toEqual([expectedTag]);
    });

    it('should render no question component for an unknown question type', () => {
        const question = new MultipleChoiceQuestion();
        question.type = undefined;
        fixture.componentRef.setInput('question', question);
        fixture.detectChanges();

        expect(renderedQuestionTags()).toEqual([]);
        expect(fixture.nativeElement.querySelector('[data-testid="quiz-question-preview"]')).not.toBeNull();
    });

    it('should pass the question and its one-based position to the question component', () => {
        const question = new MultipleChoiceQuestion();
        question.title = 'Question title';
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('questionIndex', 3);
        fixture.detectChanges();

        const child = fixture.debugElement.query(By.directive(MockComponent(MultipleChoiceQuestionComponent)));
        const instance = child.componentInstance as MultipleChoiceQuestionComponent;
        expect(instance.question()).toBe(question);
        expect(instance.questionIndex()).toBe(3);
        expect(instance.selectedAnswerOptions()).toEqual([]);
    });
});

describe('QuizQuestionPreviewComponent with the real drag and drop question component', () => {
    let fixture: ComponentFixture<QuizQuestionPreviewComponent>;

    const createDragAndDropQuestion = (id: number) => {
        const dropLocation = new DropLocation();
        dropLocation.tempID = 1;
        const dragItem = new DragItem();
        dragItem.tempID = 2;
        const question = new DragAndDropQuestion();
        question.id = id;
        question.type = QuizQuestionType.DRAG_AND_DROP;
        question.backgroundFilePath = '';
        question.dropLocations = [dropLocation];
        question.dragItems = [dragItem];
        question.correctMappings = [];
        return { question, dropLocation, dragItem };
    };

    const dropItem = (dragItem: DragItem, dropLocation: DropLocation) => {
        const component = fixture.debugElement.query(By.directive(DragAndDropQuestionComponent)).componentInstance as DragAndDropQuestionComponent;
        component.onDragDrop(dropLocation, { item: { data: dragItem } } as CdkDragDrop<DragItem, DragItem>);
        fixture.detectChanges();
        return component;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizQuestionPreviewComponent],
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // The other question types, the image and the scoring modal are not part of what this checks: that a dropped item stays where it was dropped.
            .overrideComponent(QuizQuestionPreviewComponent, {
                remove: { imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent] },
                add: { imports: [MockComponent(MultipleChoiceQuestionComponent), MockComponent(ShortAnswerQuestionComponent)] },
            })
            .overrideComponent(DragAndDropQuestionComponent, {
                remove: { imports: [ImageComponent, QuizScoringInfoStudentModalComponent] },
                add: { imports: [MockComponent(ImageComponent), MockComponent(QuizScoringInfoStudentModalComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(QuizQuestionPreviewComponent);
    });

    it('should keep a dropped item assigned to its drop location without changing the question', () => {
        const { question, dropLocation, dragItem } = createDragAndDropQuestion(1);
        fixture.componentRef.setInput('question', question);
        fixture.detectChanges();

        const component = dropItem(dragItem, dropLocation);

        expect(component.mappings()).toHaveLength(1);
        expect(component.mappings()[0].dragItem).toBe(dragItem);
        expect(component.mappings()[0].dropLocation).toBe(dropLocation);
        expect(question.correctMappings).toEqual([]);
    });

    it('should move an item back out of its drop location when it is dropped on the unassigned items', () => {
        const { question, dropLocation, dragItem } = createDragAndDropQuestion(1);
        fixture.componentRef.setInput('question', question);
        fixture.detectChanges();
        const component = dropItem(dragItem, dropLocation);
        expect(component.mappings()).toHaveLength(1);

        component.onDragDrop(undefined, { item: { data: dragItem } } as CdkDragDrop<DragItem, DragItem>);
        fixture.detectChanges();

        expect(component.mappings()).toEqual([]);
    });

    it('should start without dropped items when another question is previewed', () => {
        const first = createDragAndDropQuestion(1);
        fixture.componentRef.setInput('question', first.question);
        fixture.detectChanges();
        expect(dropItem(first.dragItem, first.dropLocation).mappings()).toHaveLength(1);

        const second = createDragAndDropQuestion(2);
        fixture.componentRef.setInput('question', second.question);
        fixture.detectChanges();

        const component = fixture.debugElement.query(By.directive(DragAndDropQuestionComponent)).componentInstance as DragAndDropQuestionComponent;
        expect(component.mappings()).toEqual([]);
    });
});
