import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbCollapse, NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { DragItemComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-item.component';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisTestModule } from '../../test.module';
import { FitTextModule } from 'app/exercises/quiz/shared/fit-text/fit-text.module';

describe('DragAndDropQuestionComponent', () => {
    let fixture: ComponentFixture<DragAndDropQuestionComponent>;
    let comp: DragAndDropQuestionComponent;
    let markdownService: ArtemisMarkdownService;
    let dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    const reset = () => {
        const question = new DragAndDropQuestion();
        question.id = 1;
        question.backgroundFilePath = '';
        comp.question = question;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbPopoverModule, ArtemisTestModule, DragDropModule, FitTextModule],
            declarations: [
                DragAndDropQuestionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(MarkdownEditorComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockDirective(NgbCollapse),
                MockComponent(QuizScoringInfoStudentModalComponent),
                DragItemComponent,
            ],
            providers: [MockProvider(DragAndDropQuestionUtil)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragAndDropQuestionComponent);
                comp = fixture.componentInstance;
                reset();
                markdownService = TestBed.inject(ArtemisMarkdownService);
                dragAndDropQuestionUtil = fixture.debugElement.injector.get(DragAndDropQuestionUtil);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update html when question changes', () => {
        const question = new DragAndDropQuestion();
        question.text = 'Test text';
        question.hint = 'Test hint';
        question.explanation = 'Test explanation';
        const markdownSpy = jest.spyOn(markdownService, 'safeHtmlForMarkdown').mockImplementation((arg) => `${arg}markdown`);
        comp.question = question;
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(comp.renderedQuestion).toBeDefined();
        expect(comp.renderedQuestion.text).toEqual(`${question.text}markdown`);
        expect(comp.renderedQuestion.hint).toEqual(`${question.hint}markdown`);
        expect(comp.renderedQuestion.explanation).toEqual(`${question.explanation}markdown`);
    });

    it('should count correct mappings as zero if no correct mappings', () => {
        const { dropLocation } = getDropLocationMappingAndItem();
        comp.question.dropLocations = [dropLocation];
        comp.ngOnChanges();
        expect(comp.correctAnswer).toBe(0);
    });

    it('should count correct mappings on changes', () => {
        const { dropLocation: dropLocation1, mapping: correctMapping1 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation2, mapping: correctMapping2 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation3, mapping: correctMapping3 } = getDropLocationMappingAndItem();
        const { mapping: correctMapping4 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation5 } = getDropLocationMappingAndItem();
        comp.question.dropLocations = [dropLocation1, dropLocation2, dropLocation3];
        // Mappings do not have any of drop locations so no selected item
        comp.mappings = [correctMapping4];
        comp.question.correctMappings = [correctMapping1, correctMapping2, correctMapping4];
        comp.ngOnChanges();
        /*
         *   without selected items it should not set correct answers to drop locations without valid drag item
         *   as they are excluded from the score calculation as well
         */
        expect(comp.correctAnswer).toBe(0);

        // if there is a selected item should count drop locations that have the selected items drag item
        // dropLocation1 and dropLocation3 is selected
        // dropLocation1 is both correct and selected
        // dropLocation2 is correct but not selected
        // dropLocation3 is not correct but selected
        // dropLocation4 is not one of the drop locations
        // dropLocation5 is neither correct nor selected
        // Hence 1 because of dropLocation1 (dropLocation5 must be excluded)
        comp.mappings = [correctMapping1, correctMapping3];
        comp.question.dropLocations = [dropLocation1, dropLocation2, dropLocation3, dropLocation5];
        comp.ngOnChanges();
        expect(comp.correctAnswer).toBe(1);
    });

    it('should return correct drag item for drop location', () => {
        const { dropLocation, mapping, dragItem } = getDropLocationMappingAndItem();
        const { dropLocation: falseDropLocation } = getDropLocationMappingAndItem();
        comp.sampleSolutionMappings = [mapping];
        expect(comp.correctDragItemForDropLocation(dropLocation)).toEqual(dragItem);
        expect(comp.correctDragItemForDropLocation(falseDropLocation)).toBeUndefined();
    });

    it('should show sample solution if force sample solution is set to true', () => {
        const { mapping } = getDropLocationMappingAndItem();
        const mappings = [mapping];
        const solveSpy = jest.spyOn(dragAndDropQuestionUtil, 'solve').mockReturnValue(mappings);
        comp.mappings = mappings;
        comp.forceSampleSolution = true;
        expect(comp.forceSampleSolution).toBeTrue();
        expect(solveSpy).toHaveBeenCalledWith(comp.question, mappings);
        expect(comp.sampleSolutionMappings).toEqual(mappings);
        expect(comp.showingSampleSolution).toBeTrue();
    });

    it('should hide sample solutions', () => {
        comp.showingSampleSolution = true;
        comp.hideSampleSolution();
        expect(comp.showingSampleSolution).toBeFalse();
    });

    it('should return unassigned drag items', () => {
        const { mapping: mapping1, dragItem: dragItem1 } = getDropLocationMappingAndItem();
        const { dragItem: dragItem2 } = getDropLocationMappingAndItem();
        comp.mappings = [mapping1];
        comp.question.dragItems = [dragItem1, dragItem2];
        expect(comp.getUnassignedDragItems()).toEqual([dragItem2]);
    });

    it('should return invalid dragItem for location', () => {
        const { dropLocation: dropLocation1, mapping: mapping1 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation2, mapping: mapping2, dragItem: dragItem2 } = getDropLocationMappingAndItem();
        comp.mappings = [mapping1, mapping2];
        expect(comp.invalidDragItemForDropLocation(dropLocation1)).toBeFalse();
        dragItem2.invalid = true;
        expect(comp.invalidDragItemForDropLocation(dropLocation2)).toBeTrue();
    });

    it('should return no drag item if there is no mapping', () => {
        const { dropLocation } = getDropLocationMappingAndItem();
        expect(comp.dragItemForDropLocation(dropLocation)).toBeUndefined();
    });

    it('should remove existing mappings when there is no drop location', () => {
        const { mapping, dragItem } = getDropLocationMappingAndItem();
        const { mapping: mapping1 } = getDropLocationMappingAndItem();
        const { mapping: mapping2 } = getDropLocationMappingAndItem();
        checkDragDrop(undefined, dragItem, [mapping, mapping1], [mapping1], 1);

        // should not call update if mappings did not change
        checkDragDrop(undefined, dragItem, [mapping1, mapping2], [mapping1, mapping2], 0);
    });

    it('should not do anything if given dropLocation and dragEvent dragData is mapped', () => {
        const { dropLocation, mapping, dragItem } = getDropLocationMappingAndItem();
        checkDragDrop(dropLocation, dragItem, [mapping], [mapping], 0);
    });

    it('should map dragItem to new drop location', () => {
        const { dropLocation, mapping, dragItem } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation1, mapping: mapping1, dragItem: dragItem1 } = getDropLocationMappingAndItem();
        const { mapping: mapping2 } = getDropLocationMappingAndItem();
        const newMappings = [mapping2, new DragAndDropMapping(dragItem1, dropLocation), new DragAndDropMapping(dragItem, dropLocation1)];
        checkDragDrop(dropLocation, dragItem1, [mapping, mapping1, mapping2], newMappings, 1);
    });

    const checkDragDrop = (
        dropLocation: DropLocation | undefined,
        dragItem: DragItem,
        mappings: DragAndDropMapping[],
        expectedMappings: DragAndDropMapping[],
        callCount: number,
    ) => {
        comp.mappings = mappings;
        const event = { item: { data: dragItem } } as CdkDragDrop<DragItem, DragItem>;
        const onMappingUpdate = jest.fn();
        comp.onMappingUpdate = onMappingUpdate;
        comp.onDragDrop(dropLocation, event);
        expect(comp.mappings).toEqual(expectedMappings);
        expect(onMappingUpdate.mock.calls.length).toBe(callCount);
    };

    it('should change loading with given value', () => {
        comp.changeLoading('loading');
        expect(comp.loadingState).toBe('loading');
    });

    it('should set drop allowed to true when dragged', () => {
        comp.dropAllowed = false;
        comp.drag();
        expect(comp.dropAllowed).toBeTrue();
    });

    const getDropLocationMappingAndItem = () => {
        const dropLocation = new DropLocation();
        const dragItem = new DragItem();
        const mapping = new DragAndDropMapping(dragItem, dropLocation);
        return { dropLocation, mapping, dragItem };
    };
});
