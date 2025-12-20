import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { QuizScoringInfoStudentModalComponent } from 'app/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { FitTextDirective } from 'app/quiz/shared/fit-text/fit-text.directive';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ImageComponent } from '../../../../shared/image/image.component';

describe('DragAndDropQuestionComponent', () => {
    let fixture: ComponentFixture<DragAndDropQuestionComponent>;
    let comp: DragAndDropQuestionComponent;
    let markdownService: ArtemisMarkdownService;
    let dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    const reset = () => {
        const question = new DragAndDropQuestion();
        question.id = 1;
        question.backgroundFilePath = '';
        fixture.componentRef.setInput('question', question);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DragDropModule, FitTextDirective, FontAwesomeModule],
            declarations: [
                DragAndDropQuestionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ImageComponent),
                MockComponent(QuizScoringInfoStudentModalComponent),
                DragItemComponent,
            ],
            providers: [MockProvider(DragAndDropQuestionUtil), { provide: ProfileService, useClass: MockProfileService }],
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
        jest.spyOn(comp, 'hideSampleSolution');
        const question = new DragAndDropQuestion();
        question.text = 'Test text';
        question.hint = 'Test hint';
        question.explanation = 'Test explanation';
        const markdownSpy = jest.spyOn(markdownService, 'safeHtmlForMarkdown').mockImplementation((arg) => `${arg}markdown`);
        fixture.componentRef.setInput('question', question);
        fixture.changeDetectorRef.detectChanges();
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(markdownSpy).toHaveBeenCalledWith(question.text);
        expect(comp.renderedQuestion).toBeDefined();
        expect(comp.renderedQuestion.text).toBe(`${question.text}markdown`);
        expect(comp.renderedQuestion.hint).toBe(`${question.hint}markdown`);
        expect(comp.renderedQuestion.explanation).toBe(`${question.explanation}markdown`);
        expect(comp.hideSampleSolution).toHaveBeenCalledOnce();
        expect(comp.showingSampleSolution()).toBeFalsy();
    });

    it('should count correct mappings as zero if no correct mappings', () => {
        const { dropLocation } = getDropLocationMappingAndItem();
        comp.dragAndDropQuestion().dropLocations = [dropLocation];
        fixture.changeDetectorRef.detectChanges();
        expect(comp.correctAnswer).toBe(0);
    });

    it('should count correct mappings on changes', () => {
        const { dropLocation: dropLocation1, mapping: correctMapping1 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation2, mapping: correctMapping2 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation3, mapping: correctMapping3 } = getDropLocationMappingAndItem();
        const { mapping: correctMapping4 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation5 } = getDropLocationMappingAndItem();
        comp.dragAndDropQuestion().dropLocations = [dropLocation1, dropLocation2, dropLocation3];
        // Mappings do not have any of drop locations so no selected item
        fixture.componentRef.setInput('mappings', [correctMapping4]);
        fixture.changeDetectorRef.detectChanges();
        comp.dragAndDropQuestion().correctMappings = [correctMapping1, correctMapping2, correctMapping4];
        fixture.changeDetectorRef.detectChanges();
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
        fixture.componentRef.setInput('mappings', [correctMapping1, correctMapping3]);
        fixture.changeDetectorRef.detectChanges();
        comp.dragAndDropQuestion().dropLocations = [dropLocation1, dropLocation2, dropLocation3, dropLocation5];
        fixture.changeDetectorRef.detectChanges();
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
        fixture.componentRef.setInput('mappings', mappings);
        fixture.componentRef.setInput('forceSampleSolution', false);
        fixture.changeDetectorRef.detectChanges();
        fixture.componentRef.setInput('forceSampleSolution', true);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.forceSampleSolution()).toBeTrue();
        expect(solveSpy).toHaveBeenCalledWith(comp.question(), mappings);
        expect(comp.sampleSolutionMappings).toEqual(mappings);
        expect(comp.showingSampleSolution()).toBeTrue();
    });

    it('should hide sample solutions', () => {
        comp.showingSampleSolution.set(true);
        comp.hideSampleSolution();
        expect(comp.showingSampleSolution()).toBeFalse();
    });

    it('should return unassigned drag items', () => {
        const { mapping: mapping1, dragItem: dragItem1 } = getDropLocationMappingAndItem();
        const { dragItem: dragItem2 } = getDropLocationMappingAndItem();
        fixture.componentRef.setInput('mappings', [mapping1]);
        fixture.changeDetectorRef.detectChanges();
        comp.dragAndDropQuestion().dragItems = [dragItem1, dragItem2];
        expect(comp.getUnassignedDragItems()).toEqual([dragItem2]);
    });

    it('should return invalid dragItem for location', () => {
        const { dropLocation: dropLocation1, mapping: mapping1 } = getDropLocationMappingAndItem();
        const { dropLocation: dropLocation2, mapping: mapping2, dragItem: dragItem2 } = getDropLocationMappingAndItem();
        fixture.componentRef.setInput('mappings', [mapping1, mapping2]);
        fixture.changeDetectorRef.detectChanges();
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
        fixture.componentRef.setInput('mappings', mappings);
        fixture.changeDetectorRef.detectChanges();
        const event = { item: { data: dragItem } } as CdkDragDrop<DragItem, DragItem>;
        const onMappingUpdate = jest.fn();
        fixture.componentRef.setInput('onMappingUpdate', onMappingUpdate);
        comp.onDragDrop(dropLocation, event);
        expect(comp._mappings).toEqual(expectedMappings);
        expect(onMappingUpdate.mock.calls).toHaveLength(callCount);
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
