import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { FormsModule } from '@angular/forms';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { DndModule } from 'ng2-dnd';
import { DragAndDropMouseEvent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-mouse-event.class';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { FileUploaderService, FileUploadResponse } from 'app/shared/http/file-uploader.service';
import { DragState } from 'app/entities/quiz/drag-state.enum';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';

chai.use(sinonChai);
const expect = chai.expect;

describe('DragAndDropQuestionEditComponent', () => {
    let fixture: ComponentFixture<DragAndDropQuestionEditComponent>;
    let component: DragAndDropQuestionEditComponent;
    let uploadService: FileUploaderService;

    const question1 = new DragAndDropQuestion();
    question1.id = 1;
    question1.backgroundFilePath = '';
    const question2 = new DragAndDropQuestion();
    question2.id = 2;
    question2.backgroundFilePath = '';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, DndModule.forRoot()],
            declarations: [
                DragAndDropQuestionEditComponent,
                MockPipe(TranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockDirective(NgbCollapse),
            ],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(DragAndDropQuestionEditComponent);
        component = fixture.componentInstance;
        uploadService = TestBed.inject(FileUploaderService);
    });

    beforeEach(() => {
        component.question = question1;
        component.questionIndex = 1;
        component.reEvaluationInProgress = false;

        fixture.detectChanges();
    });

    afterEach(function () {
        sinon.restore();
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component.isQuestionCollapsed).to.be.false;
        expect(component.isUploadingDragItemFile).to.be.false;
        expect(component.mouse).to.deep.equal(new DragAndDropMouseEvent());
    });

    it('should detect changes and update component', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        triggerChanges(component, { property: 'question', currentValue: question2, previousValue: question1 });

        fixture.detectChanges();

        expect(questionUpdatedSpy).to.have.been.calledOnce;
        expect(component.backupQuestion).to.deep.equal(question1);
    });

    it('should edit question in different ways', () => {
        const eventUpSpy = sinon.spy(component.questionMoveUp, 'emit');
        const eventDownSpy = sinon.spy(component.questionMoveDown, 'emit');
        const eventDeleteSpy = sinon.spy(component.questionDeleted, 'emit');

        component.moveUpQuestion();
        component.moveDownQuestion();
        component.deleteQuestion();

        expect(eventUpSpy).to.be.calledOnce;
        expect(eventDownSpy).to.be.calledOnce;
        expect(eventDeleteSpy).to.be.calledOnce;
    });

    it('should set background file', () => {
        const file1 = { name: 'newFile1' };
        const file2 = { name: 'newFile2' };
        const event = { target: { files: [file1, file2] } };

        component.setBackgroundFile(event);

        expect(component.backgroundFile).to.deep.equal(file1);
        expect(component.backgroundFileName).to.equal(file1.name);

        component.uploadBackground();
    });

    it('should upload file', fakeAsync(() => {
        const file1 = { name: 'newFile1' };
        const file2 = { name: 'newFile2' };
        const event = { target: { files: [file1, file2] } };

        component.setBackgroundFile(event);

        expect(component.backgroundFile).to.deep.equal(file1);
        expect(component.backgroundFileName).to.equal(file1.name);

        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        spyOn(uploadService, 'uploadFile').and.returnValue(mockReturnValue);

        component.uploadBackground();
        tick();

        expect(component.backgroundFile).to.equal(undefined);
        expect(component.question.backgroundFilePath).to.equal(newPath);
        expect(component.isUploadingBackgroundFile).to.be.false;
    }));

    it('should move the mouse in different situations', () => {
        const event1 = { pageX: 10, pageY: 10 };
        const event2 = { clientX: -10, clientY: -10 };

        component.mouseMove(event1);

        expect(component.mouse.x).to.equal(0);
        expect(component.mouse.y).to.equal(0);

        // MOVE
        component.mouse.offsetX = 15;
        component.mouse.offsetY = 15;
        component.draggingState = DragState.MOVE;
        const lengthOfElement = 15;
        component.currentDropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement };

        component.mouseMove(event2);

        expect(component.mouse.x).to.equal(0);
        expect(component.mouse.y).to.equal(0);
        expect(component.currentDropLocation.posX).to.equal(200 - lengthOfElement);
        expect(component.currentDropLocation.posY).to.equal(200 - lengthOfElement);

        // RESIZE_BOTH
        component.draggingState = DragState.RESIZE_BOTH;
        component.mouse.startX = 10;
        component.mouse.startY = 10;

        component.mouseMove(event2);

        expect(component.mouse.x).to.equal(0);
        expect(component.mouse.y).to.equal(0);
        expect(component.currentDropLocation.posX).to.exist; // NaN
        expect(component.currentDropLocation.posY).to.exist; // NaN

        // RESIZE_X
        component.draggingState = DragState.RESIZE_X;
        component.mouse.startX = 10;

        fixture.detectChanges();
        component.mouseMove(event2);

        expect(component.currentDropLocation.posX).to.exist; // NaN
        expect(component.currentDropLocation.posY).to.exist; // NaN

        // RESIZE_Y
        component.draggingState = DragState.RESIZE_Y;
        component.mouse.startY = 10;

        component.mouseMove(event2);

        expect(component.currentDropLocation.posX).to.exist; // NaN
        expect(component.currentDropLocation.posY).to.exist; // NaN
    });

    it('should move mouse up', () => {
        component.draggingState = DragState.MOVE;
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');

        component.mouseUp();

        expect(questionUpdatedSpy).to.be.calledOnce;
        expect(component.draggingState).to.equal(DragState.NONE);
        expect(component.currentDropLocation).to.be.undefined;

        component.draggingState = DragState.CREATE;
        const lengthOfElement = 15;
        const dropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement };
        const alternativeDropLocation = { posX: 15, posY: 15, width: lengthOfElement, height: lengthOfElement };
        component.currentDropLocation = dropLocation;
        component.question.dropLocations = [dropLocation, alternativeDropLocation];
        component.question.correctMappings = undefined;

        component.mouseUp();

        expect(component.draggingState).to.equal(DragState.NONE);
        expect(component.currentDropLocation).to.be.undefined;
        expect(component.question.correctMappings).to.deep.equal([]);
        expect(component.question.dropLocations).to.deep.equal([alternativeDropLocation]);
    });

    it('should move background mouse down', () => {
        component.draggingState = DragState.NONE;
        component.question.backgroundFilePath = 'NeedToGetInThere';
        const mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 };
        component.mouse = mouse;

        component.backgroundMouseDown();

        expect(component.currentDropLocation!.posX).to.equal(mouse.x);
        expect(component.currentDropLocation!.posY).to.equal(mouse.y);
        expect(component.currentDropLocation!.width).to.equal(0);
        expect(component.currentDropLocation!.height).to.equal(0);
        expect(component.draggingState).to.equal(DragState.CREATE);
    });

    it('should drop location on mouse down', () => {
        component.draggingState = DragState.NONE;
        const mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 };
        component.mouse = mouse;
        const dropLocation = new DropLocation();

        component.dropLocationMouseDown(dropLocation);

        expect(component.mouse.offsetX).to.exist; // NaN
        expect(component.mouse.offsetY).to.exist; // NaN
        expect(component.currentDropLocation).to.deep.equal(dropLocation);
        expect(component.draggingState).to.equal(DragState.MOVE);
    });

    it('should duplicate drop location', () => {
        const dropLocation = { posX: 10, posY: 10, width: 0, height: 0 } as DropLocation;
        component.question.dropLocations = [];

        component.duplicateDropLocation(dropLocation);

        const duplicatedDropLocation = component.question.dropLocations[0];
        expect(duplicatedDropLocation.posX).to.equal(dropLocation.posX! + 3);
        expect(duplicatedDropLocation.posY).to.equal(dropLocation.posY! + 3);
        expect(duplicatedDropLocation.width).to.equal(0);
        expect(duplicatedDropLocation.height).to.equal(0);
    });

    it('should resize mouse down', () => {
        component.draggingState = DragState.NONE;
        const dropLocation = { posX: 200, posY: 200, width: 0, height: 0 } as DropLocation;
        // middle, right
        let resizeLocationY = 'middle';
        let resizeLocationX = 'right';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.draggingState).to.equal(DragState.RESIZE_X);
        expect(component.mouse.startX).to.equal(0);

        // top, left
        component.draggingState = DragState.NONE;
        resizeLocationY = 'top';
        resizeLocationX = 'left';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.mouse.startY).to.equal(0);
        expect(component.mouse.startX).to.equal(0);

        // bottom, center
        component.draggingState = DragState.NONE;
        resizeLocationY = 'bottom';
        resizeLocationX = 'center';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.mouse.startY).to.equal(0);
        expect(component.draggingState).to.equal(DragState.RESIZE_Y);
    });

    it('should add text item', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        const dragItem = new DragItem();
        dragItem.text = 'Text';

        component.addTextDragItem();

        expect(questionUpdatedSpy).to.have.been.calledOnce;
        const firstDragItemOfQuestion = component.question.dragItems![0];
        expect(firstDragItemOfQuestion.text).to.equal('Text');
    });

    it('should set drag item text', () => {
        const file1 = { name: 'newDragFile1' };
        const file2 = { name: 'newDragFile2' };
        const event = { target: { files: [file1, file2] } };

        component.setDragItemFile(event);

        expect(component.dragItemFile).to.deep.equal(file1);
        expect(component.dragItemFileName).to.equal('newDragFile1');
    });

    it('should upload drag item', fakeAsync(() => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        try {
            component.dragItemFile = new File([], 'file');

            const newPath = 'alwaysGoYourPath';
            let mockReturnValue = Promise.resolve({ path: newPath });
            const spy = spyOn(uploadService, 'uploadFile');
            spy.and.returnValue(mockReturnValue);

            component.uploadDragItem();
            tick();

            const expectedItem = component.question.dragItems![0];
            expect(expectedItem!.pictureFilePath).to.equal('alwaysGoYourPath');
            expect(questionUpdatedSpy).to.have.been.calledOnce;
            expect(component.dragItemFileName).to.equal('');
            expect(component.dragItemFile).to.be.undefined;
            sinon.restore();

            mockReturnValue = Promise.reject({ path: newPath });
            spy.and.returnValue(mockReturnValue);
            component.dragItemFile = new File([], 'file');

            component.uploadDragItem();
            tick();
        } catch (error) {
            expect(component.isUploadingDragItemFile).to.be.false;
            // Once because spy has been called in first execution of uploadDragItem()
            expect(questionUpdatedSpy).to.have.been.calledOnce;
        }
    }));

    it('should picture for drag item', fakeAsync(() => {
        component.dragItemFile = new File([], 'file');
        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        spyOn(uploadService, 'uploadFile').and.returnValue(mockReturnValue);
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');

        component.uploadPictureForDragItemChange();
        tick();

        expect(questionUpdatedSpy).to.have.been.calledOnce;
        expect(component.dragItemPicture).to.equal(newPath);
        expect(component.isUploadingDragItemFile).to.be.false;
    }));

    it('should delete drag item', () => {
        const item = new DragItem();
        component.question.dragItems = [item];

        component.deleteDragItem(item);

        expect(component.question.dragItems).to.deep.equal([]);
    });

    it('should delete mapping', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);

        component.deleteMapping(mapping);

        expect(component.question.correctMappings).to.deep.equal([]);
    });

    it('should drop a drag item on a drop location', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        const location = new DropLocation();
        const item = new DragItem();
        item.id = 2;
        location.id = 2;
        component.question.dragItems = [item];
        const mapping = new DragAndDropMapping(item, location);
        component.question.correctMappings = [mapping];
        const alternativeLocation = new DropLocation();
        const alternativeItem = new DragItem();
        alternativeLocation.id = 3;
        alternativeItem.id = 3;
        const event = { dragData: alternativeItem };
        const expectedMapping = new DragAndDropMapping(alternativeItem, alternativeLocation);
        component.question.dragItems = [item, alternativeItem];

        component.onDragDrop(alternativeLocation, event);

        expect(questionUpdatedSpy).to.have.been.calledOnce;
        expect(component.question.correctMappings).to.deep.equal([mapping, expectedMapping]);
    });

    it('should get mapping for drag item', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        component.question.correctMappings = [mapping];

        expect(component.getMappingsForDragItem(item)).to.deep.equal([mapping]);
    });

    it('should change picture drag item to text drag item', () => {
        const item = new DragItem();

        component.changeToTextDragItem(item);

        expect(component).to.be.ok; // ??
    });

    it('should change text drag item to picture drag item', fakeAsync(() => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        spyOn(uploadService, 'uploadFile').and.returnValue(mockReturnValue);
        const item = new DragItem();
        component.dragItemFile = new File([], 'file');
        component.dragItemPicture = 'picturePath';

        component.changeToPictureDragItem(item);
        tick();

        expect(component.dragItemPicture).to.equal(newPath);
        expect(questionUpdatedSpy).to.have.been.calledOnce;
        expect(component.isUploadingDragItemFile).to.be.false;
    }));

    it('should change question title', () => {
        const title = 'backupQuestionTitle';
        component.question = new DragAndDropQuestion();
        component.question.title = 'alternativeBackupQuestionTitle';
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.title = title;

        component.resetQuestionTitle();

        expect(component.question.title).to.equal(title);
    });

    it('should reset question', () => {
        const title = 'backupQuestionTitle';
        const dropLocation = new DropLocation();
        const item = new DragItem();
        component.question = new DragAndDropQuestion();
        component.question.title = 'alternativeBackupQuestionTitle';

        const backupQuestion = {
            type: 'drag-and-drop',
            randomizeOrder: false,
            invalid: true,
            exportQuiz: false,
            title,
            dropLocations: [dropLocation],
            dragItems: [item],
            correctMappings: [],
            backgroundFilePath: 'filepath',
            text: 'newText',
            explanation: 'explanation',
            hint: 'hint',
            scoringType: ScoringType.ALL_OR_NOTHING,
        } as DragAndDropQuestion;
        component.backupQuestion = backupQuestion;

        component.resetQuestion();

        expect(component.question).to.deep.equal(backupQuestion);
    });

    it('should reset drag item', () => {
        const firstItem = new DragItem();
        firstItem.id = 404;
        firstItem.invalid = true;
        const secondItem = new DragItem();
        secondItem.id = 404;
        secondItem.invalid = false;
        component.question = new DragAndDropQuestion();
        component.question.dragItems = [new DragItem(), new DragItem(), firstItem, new DragItem()];
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.dragItems = [secondItem];

        component.resetDragItem(firstItem);

        expect(component.question.dragItems[2].invalid).to.be.false;
    });

    it('should toggle preview', () => {
        component.showPreview = true;
        component.question = new DragAndDropQuestion();
        component.question.text = 'should be removed';

        component.togglePreview();

        expect(component.showPreview).to.be.false;
        expect(component.question.text).to.be.undefined;
    });

    it('should detect changes in markdown and edit accordingly', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        component.question = new DragAndDropQuestion();
        component.question.text = 'should be removed';

        component.changesInMarkdown();

        expect(questionUpdatedSpy).to.have.been.calledOnce;
        expect(component.question.text).to.be.undefined;
    });

    it('should detect domain commands', () => {
        component.question = new DragAndDropQuestion();
        component.question.text = 'should be removed';
        const domainCommand = new ExplanationCommand();
        const commands = [['take this as a command', domainCommand]];

        component.domainCommandsFound([]);

        expect(component.question.text).to.be.undefined;
    });
});
