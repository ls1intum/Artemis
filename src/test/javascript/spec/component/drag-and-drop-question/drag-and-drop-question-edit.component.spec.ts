import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { NgbCollapse, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragState } from 'app/entities/quiz/drag-state.enum';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropMouseEvent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-mouse-event.class';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { FileUploadResponse, FileUploaderService } from 'app/shared/http/file-uploader.service';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { clone } from 'lodash-es';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../test.module';

describe('DragAndDropQuestionEditComponent', () => {
    let fixture: ComponentFixture<DragAndDropQuestionEditComponent>;
    let component: DragAndDropQuestionEditComponent;
    let uploadService: FileUploaderService;
    let modalService: NgbModal;

    const question1 = new DragAndDropQuestion();
    question1.id = 1;
    question1.backgroundFilePath = '';
    const question2 = new DragAndDropQuestion();
    question2.id = 2;
    question2.backgroundFilePath = '';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DragDropModule],
            declarations: [
                DragAndDropQuestionEditComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockDirective(NgbCollapse),
                MockDirective(NgModel),
            ],
            providers: [
                MockProvider(ArtemisMarkdownService),
                MockProvider(DragAndDropQuestionUtil),
                MockProvider(FileUploaderService),
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(ChangeDetectorRef),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(DragAndDropQuestionEditComponent);
        component = fixture.componentInstance;
        uploadService = TestBed.inject(FileUploaderService);
        modalService = TestBed.inject(NgbModal);
    });

    beforeEach(fakeAsync(() => {
        component.question = question1;
        component.questionIndex = 1;
        component.reEvaluationInProgress = false;

        fixture.detectChanges(false);
        tick();
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component.isQuestionCollapsed).toBeFalse();
        expect(component.isUploadingDragItemFile).toBeFalse();
        expect(component.mouse).toStrictEqual(new DragAndDropMouseEvent());
    });

    it('should detect changes and update component', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        triggerChanges(component, { property: 'question', currentValue: question2, previousValue: question1 });

        fixture.detectChanges();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.backupQuestion).toEqual(question1);
    });

    it('should edit question in different ways', () => {
        const eventUpSpy = jest.spyOn(component.questionMoveUp, 'emit');
        const eventDownSpy = jest.spyOn(component.questionMoveDown, 'emit');
        const eventDeleteSpy = jest.spyOn(component.questionDeleted, 'emit');

        component.moveUpQuestion();
        component.moveDownQuestion();
        component.deleteQuestion();

        expect(eventUpSpy).toHaveBeenCalledOnce();
        expect(eventDownSpy).toHaveBeenCalledOnce();
        expect(eventDeleteSpy).toHaveBeenCalledOnce();
    });

    it('should set background file', () => {
        const file1 = { name: 'newFile1' };
        const file2 = { name: 'newFile2' };
        const event = { target: { files: [file1, file2] } };
        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        jest.spyOn(uploadService, 'uploadFile').mockReturnValue(mockReturnValue);

        component.setBackgroundFile(event);

        expect(component.backgroundFile).toEqual(file1);
        expect(component.backgroundFileName).toBe(file1.name);

        component.uploadBackground();
    });

    it('should upload file', fakeAsync(() => {
        const file1 = { name: 'newFile1' };
        const file2 = { name: 'newFile2' };
        const event = { target: { files: [file1, file2] } };

        component.setBackgroundFile(event);

        expect(component.backgroundFile).toEqual(file1);
        expect(component.backgroundFileName).toBe(file1.name);

        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        jest.spyOn(uploadService, 'uploadFile').mockReturnValue(mockReturnValue);

        component.uploadBackground();
        tick();

        expect(component.backgroundFile).toBeUndefined();
        expect(component.question.backgroundFilePath).toBe(newPath);
        expect(component.isUploadingBackgroundFile).toBeFalse();
    }));

    it('should move the mouse in different situations', () => {
        const event1 = { pageX: 10, pageY: 10 };
        const event2 = { clientX: -10, clientY: -10 };

        // @ts-ignore
        component['mouseMoveAction'](event1, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(10);
        expect(component.mouse.y).toBe(10);

        // MOVE
        component.mouse.offsetX = 15;
        component.mouse.offsetY = 15;
        component.draggingState = DragState.MOVE;
        const lengthOfElement = 15;
        component.currentDropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement, invalid: false };

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(0);
        expect(component.mouse.y).toBe(0);
        expect(component.currentDropLocation.posX).toBe(200 - lengthOfElement);
        expect(component.currentDropLocation.posY).toBe(200 - lengthOfElement);

        // RESIZE_BOTH
        component.draggingState = DragState.RESIZE_BOTH;
        component.mouse.startX = 10;
        component.mouse.startY = 10;

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(0);
        expect(component.mouse.y).toBe(0);
        expect(component.currentDropLocation.posX).toBe(0);
        expect(component.currentDropLocation.posY).toBe(0);

        // RESIZE_X
        component.draggingState = DragState.RESIZE_X;
        component.mouse.startX = 10;

        fixture.detectChanges();
        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.currentDropLocation.posX).toBe(0);
        expect(component.currentDropLocation.posY).toBe(0);

        // RESIZE_Y
        component.draggingState = DragState.RESIZE_Y;
        component.mouse.startY = 10;

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.currentDropLocation.posX).toBe(0);
        expect(component.currentDropLocation.posY).toBe(0);
    });

    it('should move mouse up', () => {
        component.draggingState = DragState.MOVE;
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');

        component.mouseUp();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.draggingState).toBe(DragState.NONE);
        expect(component.currentDropLocation).toBeUndefined();

        component.draggingState = DragState.CREATE;
        const lengthOfElement = 15;
        const dropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement, invalid: false };
        const alternativeDropLocation = { posX: 15, posY: 15, width: lengthOfElement, height: lengthOfElement, invalid: false };
        component.currentDropLocation = dropLocation;
        component.question.dropLocations = [dropLocation, alternativeDropLocation];
        component.question.correctMappings = undefined;

        component.mouseUp();

        expect(component.draggingState).toBe(DragState.NONE);
        expect(component.currentDropLocation).toBeUndefined();
        expect(component.question.correctMappings).toBeArrayOfSize(0);
        expect(component.question.dropLocations).toEqual([alternativeDropLocation]);
    });

    it('should move background mouse down', () => {
        component.draggingState = DragState.NONE;
        component.question.backgroundFilePath = 'NeedToGetInThere';
        const mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 };
        component.mouse = mouse;

        component.backgroundMouseDown();

        expect(component.currentDropLocation!.posX).toBe(mouse.x);
        expect(component.currentDropLocation!.posY).toBe(mouse.y);
        expect(component.currentDropLocation!.width).toBe(0);
        expect(component.currentDropLocation!.height).toBe(0);
        expect(component.draggingState).toBe(DragState.CREATE);
    });

    it('should drop location on mouse down', () => {
        component.draggingState = DragState.NONE;
        component.mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 };
        const dropLocation = new DropLocation();
        dropLocation.posX = dropLocation.posY = 0;
        component.dropLocationMouseDown(dropLocation);

        expect(component.mouse.offsetX).toBe(-10);
        expect(component.mouse.offsetY).toBe(-10);
        expect(component.currentDropLocation).toEqual(dropLocation);
        expect(component.draggingState).toBe(DragState.MOVE);
    });

    it('should open, drag, drop', () => {
        const content = {};
        const modalServiceSpy = jest.spyOn(modalService, 'open');

        component.open(content);
        expect(modalServiceSpy).toHaveBeenCalledOnce();
        component.drag();
        expect(component.dropAllowed).toBeTrue();
        component.drop();
        expect(component.dropAllowed).toBeFalse();
    });

    it('should duplicate drop location', () => {
        const dropLocation = { posX: 10, posY: 10, width: 0, height: 0 } as DropLocation;
        component.question.dropLocations = [];

        component.duplicateDropLocation(dropLocation);

        const duplicatedDropLocation = component.question.dropLocations[0];
        expect(duplicatedDropLocation.posX).toBe(dropLocation.posX! + 3);
        expect(duplicatedDropLocation.posY).toBe(dropLocation.posY! + 3);
        expect(duplicatedDropLocation.width).toBe(0);
        expect(duplicatedDropLocation.height).toBe(0);
    });

    it('should resize mouse down', () => {
        component.draggingState = DragState.NONE;
        const dropLocation = { posX: 200, posY: 200, width: 0, height: 0 } as DropLocation;
        // middle, right
        let resizeLocationY = 'middle';
        let resizeLocationX = 'right';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.draggingState).toBe(DragState.RESIZE_X);
        expect(component.mouse.startX).toBe(0);

        // top, left
        component.draggingState = DragState.NONE;
        resizeLocationY = 'top';
        resizeLocationX = 'left';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.mouse.startY).toBe(0);
        expect(component.mouse.startX).toBe(0);

        // bottom, center
        component.draggingState = DragState.NONE;
        resizeLocationY = 'bottom';
        resizeLocationX = 'center';

        component.resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX);

        expect(component.mouse.startY).toBe(0);
        expect(component.draggingState).toBe(DragState.RESIZE_Y);
    });

    it('should add text item', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        const dragItem = new DragItem();
        dragItem.text = 'Text';

        component.addTextDragItem();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        const firstDragItemOfQuestion = component.question.dragItems![0];
        expect(firstDragItemOfQuestion.text).toBe('Text');
    });

    it('should set drag item text', () => {
        const file1 = { name: 'newDragFile1' };
        const file2 = { name: 'newDragFile2' };
        const event = { target: { files: [file1, file2] } };

        component.setDragItemFile(event);

        expect(component.dragItemFile).toEqual(file1);
        expect(component.dragItemFileName).toBe('newDragFile1');
    });

    it('should upload drag item', fakeAsync(() => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        try {
            component.dragItemFile = new File([], 'file');

            const newPath = 'alwaysGoYourPath';
            let mockReturnValue = Promise.resolve({ path: newPath });
            const uploadFileSpy = jest.spyOn(uploadService, 'uploadFile');
            uploadFileSpy.mockReturnValue(mockReturnValue);

            component.uploadDragItem();
            tick();

            const expectedItem = component.question.dragItems![0];
            expect(expectedItem!.pictureFilePath).toBe('alwaysGoYourPath');
            expect(questionUpdatedSpy).toHaveBeenCalledOnce();
            expect(component.dragItemFileName).toBe('');
            expect(component.dragItemFile).toBeUndefined();
            jest.restoreAllMocks();

            mockReturnValue = Promise.reject({ path: newPath });
            uploadFileSpy.mockReturnValue(mockReturnValue);
            component.dragItemFile = new File([], 'file');

            component.uploadDragItem();
            tick();
        } catch (error) {
            expect(component.isUploadingDragItemFile).toBeFalse();
            // Once because spy has been called in first execution of uploadDragItem()
            expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        }
    }));

    it('should picture for drag item', fakeAsync(() => {
        component.dragItemFile = new File([], 'file');
        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        jest.spyOn(uploadService, 'uploadFile').mockReturnValue(mockReturnValue);
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');

        component.uploadPictureForDragItemChange();
        tick();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.dragItemPicture).toBe(newPath);
        expect(component.isUploadingDragItemFile).toBeFalse();
    }));

    it('should delete drag item', () => {
        const item = new DragItem();
        const newItem = new DragItem();
        const mapping = new DragAndDropMapping(newItem, new DropLocation());
        component.question.dragItems = [item];
        component.question.correctMappings = [mapping];

        component.deleteDragItem(item);

        expect(component.question.dragItems).toBeArrayOfSize(0);
        expect(component.question.correctMappings).toEqual([mapping]);
    });

    it('should delete mapping', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        component.question.correctMappings = [mapping];

        component.deleteMapping(mapping);

        expect(component.question.correctMappings).toBeArrayOfSize(0);
    });

    it('should drop a drag item on a drop location', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
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
        const event = { item: { data: alternativeItem } } as CdkDragDrop<DragItem, DragItem>;
        const expectedMapping = new DragAndDropMapping(alternativeItem, alternativeLocation);
        component.question.dragItems = [item, alternativeItem];

        component.onDragDrop(alternativeLocation, event);

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.question.correctMappings).toEqual([mapping, expectedMapping]);
    });

    it('should get mapping for drag item', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        component.question.correctMappings = [mapping];

        expect(component.getMappingsForDragItem(item)).toEqual([mapping]);
    });

    it('should change picture drag item to text drag item', () => {
        const item = new DragItem();
        const componentClone = clone(component);

        component.changeToTextDragItem(item);

        expect(component).toStrictEqual(componentClone);
    });

    it('should change text drag item to picture drag item', fakeAsync(() => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        const newPath = 'alwaysGoYourPath';
        const mockReturnValue = Promise.resolve({ path: newPath } as FileUploadResponse);
        jest.spyOn(uploadService, 'uploadFile').mockReturnValue(mockReturnValue);
        const item = new DragItem();
        component.dragItemFile = new File([], 'file');
        component.dragItemPicture = 'picturePath';

        component.changeToPictureDragItem(item);
        tick();

        expect(component.dragItemPicture).toBe(newPath);
        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.isUploadingDragItemFile).toBeFalse();
    }));

    it('should change question title', () => {
        const title = 'backupQuestionTitle';
        component.question = new DragAndDropQuestion();
        component.question.title = 'alternativeBackupQuestionTitle';
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.title = title;

        component.resetQuestionTitle();

        expect(component.question.title).toBe(title);
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

        expect(component.question).toEqual(backupQuestion);
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

        expect(component.question.dragItems[2].invalid).toBeFalse();
    });

    it('should reset drop location', () => {
        const firstItem = new DropLocation();
        firstItem.id = 404;
        firstItem.invalid = true;
        const secondItem = new DropLocation();
        secondItem.id = 404;
        secondItem.invalid = false;
        component.question = new DragAndDropQuestion();
        component.question.dropLocations = [new DropLocation(), new DropLocation(), firstItem, new DropLocation()];
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.dropLocations = [secondItem];

        component.resetDropLocation(firstItem);

        expect(component.question.dropLocations[2].invalid).toBeFalse();
    });

    it('should toggle preview', () => {
        component.showPreview = true;
        component.question = new DragAndDropQuestion();
        component.question.text = 'should be removed';

        component.togglePreview();

        expect(component.showPreview).toBeFalse();
        expect(component.question.text).toBeUndefined();
    });

    it('should detect changes in markdown and edit accordingly', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        component.question = new DragAndDropQuestion();
        component.question.text = 'should be removed';

        component.changesInMarkdown();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.question.text).toBeUndefined();
    });

    it('should detect domain commands', () => {
        component.question = new DragAndDropQuestion();
        component.question.text = 'text';
        component.question.explanation = 'explanation';
        component.question.hint = 'hint';
        let domainCommand: [string, DomainCommand];

        // explanation
        let command = new ExplanationCommand();
        let text = 'take this as an explanationCommand';
        domainCommand = [text, command];

        component.domainCommandsFound([domainCommand]);

        expect(component.question.explanation).toBe(text);

        // hint
        command = new HintCommand();
        text = 'take this as a hintCommand';
        domainCommand = [text, command];

        component.domainCommandsFound([domainCommand]);

        expect(component.question.hint).toBe(text);

        // text
        text = 'take this null as a command';
        domainCommand = [text, null as unknown as DomainCommand];

        component.domainCommandsFound([domainCommand]);

        expect(component.question.text).toBe(text);
    });
});
