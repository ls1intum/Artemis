import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DragState } from 'app/quiz/shared/entities/drag-state.enum';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragAndDropMouseEvent } from 'app/quiz/manage/drag-and-drop-question/drag-and-drop-mouse-event.class';
import { DragAndDropQuestionEditComponent } from 'app/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { MockProvider } from 'ng-mocks';
import { MockNgbModalService } from 'src/test/javascript/spec/helpers/mocks/service/mock-ngb-modal.service';
import { ChangeDetectorRef } from '@angular/core';
import { clone } from 'lodash-es';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { TextWithDomainAction } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MockResizeObserver } from 'src/test/javascript/spec/helpers/mocks/service/mock-resize-observer';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'src/test/javascript/spec/helpers/mocks/service/mock-account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'src/test/javascript/spec/helpers/mocks/service/mock-theme.service';

describe('DragAndDropQuestionEditComponent', () => {
    let fixture: ComponentFixture<DragAndDropQuestionEditComponent>;
    let component: DragAndDropQuestionEditComponent;
    let modalService: NgbModal;
    let createObjectURLStub: jest.SpyInstance;
    let questionUpdatedSpy: jest.SpyInstance;
    let addFileSpy: jest.SpyInstance;
    let removeFileSpy: jest.SpyInstance;

    const question1 = new DragAndDropQuestion();
    question1.id = 1;
    question1.backgroundFilePath = '';
    const question2 = new DragAndDropQuestion();
    question2.id = 2;
    question2.backgroundFilePath = '';
    const question3 = new DragAndDropQuestion();
    question3.id = 3;
    question3.backgroundFilePath = 'this/is/a/fake/path/1/image.jpg';
    question3.dragItems = [
        { id: 1, pictureFilePath: 'this/is/a/fake/path/2/image.jpg', text: undefined } as DragItem,
        { id: 2, pictureFilePath: 'this/is/a/fake/path/3/image.jpg', text: undefined } as DragItem,
        { id: 3, pictureFilePath: 'this/is/a/fake/path/4/image.jpg', text: undefined } as DragItem,
    ];
    const question4 = new DragAndDropQuestion();
    question4.id = 3;
    question4.backgroundFilePath = 'this/is/a/fake/path/1/image.jpg';
    question4.dragItems = [
        { id: 1, pictureFilePath: undefined, text: 'Text1' } as DragItem,
        { id: 2, pictureFilePath: undefined, text: 'Text2' } as DragItem,
        { id: 3, pictureFilePath: undefined, text: 'Text3' } as DragItem,
    ];

    const createObjectURLBackup = window.URL.createObjectURL;

    beforeAll(() => {
        window.URL.createObjectURL = jest.fn();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(DragAndDropQuestionEditComponent, {
                set: {
                    providers: [{ provide: NgbModal, useClass: MockNgbModalService }, MockProvider(ChangeDetectorRef)],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(DragAndDropQuestionEditComponent);
        component = fixture.componentInstance;
        modalService = fixture.debugElement.injector.get(NgbModal);
        fixture.componentRef.setInput('question', clone(question1));
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.componentRef.setInput('reEvaluationInProgress', false);
        questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        jest.spyOn(component['changeDetector'], 'detectChanges').mockImplementation(() => {});
        createObjectURLStub = jest.spyOn(window.URL, 'createObjectURL').mockImplementation((file: File) => {
            return 'some/client/dependent/path/' + file.name;
        });
        addFileSpy = jest.spyOn(component.addNewFile, 'emit');
        removeFileSpy = jest.spyOn(component.removeFile, 'emit');
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
    });

    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    afterAll(() => {
        window.URL.createObjectURL = createObjectURLBackup;
    });

    it('should initialize', () => {
        expect(component.backupQuestion).toEqual(question1);
        expect(component.filePreviewPaths).toEqual(new Map<string, string>());
        expect(component.mouse).toStrictEqual(new DragAndDropMouseEvent());
    });

    it('should detect changes and update component', () => {
        fixture.componentRef.setInput('question', clone(question2));
        fixture.detectChanges();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.backupQuestion).toEqual(question2);
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
        const file1 = { name: 'newFile1.jpg' } as File;
        const file2 = { name: 'newFile2.png' } as File;
        const event = { target: { files: [file1, file2] } };

        component.setBackgroundFile(event);

        expect(component.question().backgroundFilePath).toEndWith('.jpg');
        expect(addFileSpy).toHaveBeenCalledOnce();
        expect(createObjectURLStub).toHaveBeenCalledExactlyOnceWith(file1);
    });

    it('should move the mouse in different situations', () => {
        const event1 = { pageX: 10, pageY: 10 } as any;
        const event2 = { clientX: -10, clientY: -10 } as any;

        // @ts-ignore
        component['mouseMoveAction'](event1, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(10);
        expect(component.mouse.y).toBe(10);

        // MOVE
        component.mouse.offsetX = 15;
        component.mouse.offsetY = 15;
        component.draggingState = DragState.MOVE;
        const lengthOfElement = 15;
        component.currentDropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement, invalid: false } as DropLocation;

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(0);
        expect(component.mouse.y).toBe(0);
        expect(component.currentDropLocation!.posX).toBe(200 - lengthOfElement);
        expect(component.currentDropLocation!.posY).toBe(200 - lengthOfElement);

        // RESIZE_BOTH
        component.draggingState = DragState.RESIZE_BOTH;
        component.mouse.startX = 10;
        component.mouse.startY = 10;

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.mouse.x).toBe(0);
        expect(component.mouse.y).toBe(0);
        expect(component.currentDropLocation!.posX).toBe(0);
        expect(component.currentDropLocation!.posY).toBe(0);

        // RESIZE_X
        component.draggingState = DragState.RESIZE_X;
        component.mouse.startX = 10;

        fixture.detectChanges();
        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.currentDropLocation!.posX).toBe(0);
        expect(component.currentDropLocation!.posY).toBe(0);

        // RESIZE_Y
        component.draggingState = DragState.RESIZE_Y;
        component.mouse.startY = 10;

        // @ts-ignore
        component['mouseMoveAction'](event2, 0, 0, 10, 10);

        expect(component.currentDropLocation!.posX).toBe(0);
        expect(component.currentDropLocation!.posY).toBe(0);
    });

    it('should move mouse up', () => {
        component.draggingState = DragState.MOVE;
        component.mouseUp();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.draggingState).toBe(DragState.NONE);
        expect(component.currentDropLocation).toBeUndefined();

        component.draggingState = DragState.CREATE;
        const lengthOfElement = 15;
        const dropLocation = { posX: 10, posY: 10, width: lengthOfElement, height: lengthOfElement, invalid: false } as DropLocation;
        const alternativeDropLocation = { posX: 15, posY: 15, width: lengthOfElement, height: lengthOfElement, invalid: false } as DropLocation;
        component.currentDropLocation = dropLocation;
        const q = component.question();
        q.dropLocations = [dropLocation, alternativeDropLocation];
        q.correctMappings = undefined;

        component.mouseUp();

        expect(component.draggingState).toBe(DragState.NONE);
        expect(component.currentDropLocation).toBeUndefined();
        expect(q.correctMappings).toBeArrayOfSize(0);
        expect(q.dropLocations).toEqual([alternativeDropLocation]);
    });

    it('should move background mouse down', () => {
        component.draggingState = DragState.NONE;
        component.question().backgroundFilePath = 'NeedToGetInThere';
        const mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 } as any;
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
        component.mouse = { x: 10, y: 10, startX: 5, startY: 5, offsetX: 0, offsetY: 0 } as any;
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
        component.question().dropLocations = [];

        component.duplicateDropLocation(dropLocation);

        const duplicatedDropLocation = component.question().dropLocations![0];
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
        component.addTextDragItem();

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.question().dragItems).toBeArrayOfSize(1);
        const newDragItemOfQuestion = component.question().dragItems![0];
        expect(newDragItemOfQuestion.text).toBe('Text');
        expect(newDragItemOfQuestion.pictureFilePath).toBeUndefined();
        expect(component.filePreviewPaths.size).toBe(0);
        expect(addFileSpy).not.toHaveBeenCalled();
        expect(removeFileSpy).not.toHaveBeenCalled();
    });

    it('should create image item', () => {
        const extension = 'png';
        const fileName = 'testFile.' + extension;
        const expectedPath = 'some/client/dependent/path/' + fileName;
        const file = new File([], fileName);

        component.createImageDragItem({ target: { files: [file] } });

        expect(component.question().dragItems).toBeArrayOfSize(1);
        const newDragItemOfQuestion = component.question().dragItems![0];
        expect(newDragItemOfQuestion.text).toBeUndefined();
        expect(newDragItemOfQuestion.pictureFilePath).toBeDefined();
        expect(newDragItemOfQuestion.pictureFilePath).toEndWith('.' + extension);
        const filePath = newDragItemOfQuestion.pictureFilePath!;
        expect(component.filePreviewPaths.size).toBe(1);
        expect(component.filePreviewPaths.get(filePath)).toBe(expectedPath);
        expect(addFileSpy).toHaveBeenCalledExactlyOnceWith({ file, fileName: filePath });
        expect(removeFileSpy).not.toHaveBeenCalled();
    });

    it('should delete drag item', () => {
        const item = new DragItem();
        const newItem = new DragItem();
        const mapping = new DragAndDropMapping(newItem, new DropLocation());
        const q = component.question();
        q.dragItems = [item];
        q.correctMappings = [mapping];

        component.deleteDragItem(item);

        expect(q.dragItems).toBeArrayOfSize(0);
        expect(q.correctMappings).toEqual([mapping]);
    });

    it('should delete mapping', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        const q = component.question();
        q.correctMappings = [mapping];

        component.deleteMapping(mapping);

        expect(q.correctMappings).toBeArrayOfSize(0);
    });

    it('should drop a drag item on a drop location', () => {
        const location = new DropLocation();
        const item = new DragItem();
        item.id = 2;
        location.id = 2;
        const q = component.question();
        q.dragItems = [item];
        const mapping = new DragAndDropMapping(item, location);
        q.correctMappings = [mapping];
        const alternativeLocation = new DropLocation();
        const alternativeItem = new DragItem();
        alternativeLocation.id = 3;
        alternativeItem.id = 3;
        const event = { item: { data: alternativeItem } } as CdkDragDrop<DragItem, DragItem>;
        const expectedMapping = new DragAndDropMapping(alternativeItem, alternativeLocation);
        q.dragItems = [item, alternativeItem];

        component.onDragDrop(alternativeLocation, event);

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(q.correctMappings).toEqual([mapping, expectedMapping]);
    });

    it('should get mapping index for mapping', () => {
        const item1 = new DragItem();
        const item2 = new DragItem();
        const item3 = new DragItem();
        const item4 = new DragItem();
        const location1 = new DropLocation();
        const location2 = new DropLocation();
        const location3 = new DropLocation();
        const location4 = new DropLocation();
        const mapping1 = new DragAndDropMapping(item1, location1);
        const mapping2 = new DragAndDropMapping(item2, location2);
        const mapping3 = new DragAndDropMapping(item3, location3);
        const mapping4 = new DragAndDropMapping(item4, location4); // unused mapping
        const q = component.question();
        q.correctMappings = [mapping1, mapping2, mapping3];

        expect(component.getMappingIndex(mapping1)).toBe(1);
        expect(component.getMappingIndex(mapping2)).toBe(2);
        expect(component.getMappingIndex(mapping3)).toBe(3);
        expect(component.getMappingIndex(mapping4)).toBe(0);
    });

    it('should get mappings for drop location', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        const q = component.question();
        q.correctMappings = [mapping];

        expect(component.getMappingsForDropLocation(location)).toEqual([mapping]);
    });

    it('should get mappings for drag item', () => {
        const item = new DragItem();
        const location = new DropLocation();
        const mapping = new DragAndDropMapping(item, location);
        const q = component.question();
        q.correctMappings = [mapping];

        expect(component.getMappingsForDragItem(item)).toEqual([mapping]);
    });

    it('should change picture drag item to text drag item', () => {
        fixture = TestBed.createComponent(DragAndDropQuestionEditComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('question', clone(question3));
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.componentRef.setInput('reEvaluationInProgress', false);
        questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        addFileSpy = jest.spyOn(component.addNewFile, 'emit');
        removeFileSpy = jest.spyOn(component.removeFile, 'emit');
        fixture.detectChanges();

        component.changeToTextDragItem(component.question().dragItems![1]);

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.filePreviewPaths.size).toBe(3);
        expect(addFileSpy).not.toHaveBeenCalled();
        expect(removeFileSpy).toHaveBeenCalledExactlyOnceWith('this/is/a/fake/path/3/image.jpg');
        expect(component.question().dragItems![0]).toContainAllEntries([
            ['id', 1],
            ['pictureFilePath', 'this/is/a/fake/path/2/image.jpg'],
            ['text', undefined],
        ]);
        expect(component.question().dragItems![1]).toContainAllEntries([
            ['id', 2],
            ['pictureFilePath', undefined],
            ['text', 'Text'],
        ]);
        expect(component.question().dragItems![2]).toContainAllEntries([
            ['id', 3],
            ['pictureFilePath', 'this/is/a/fake/path/4/image.jpg'],
            ['text', undefined],
        ]);
    });

    it('should change text drag item to picture drag item', () => {
        fixture = TestBed.createComponent(DragAndDropQuestionEditComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('question', clone(question4));
        fixture.componentRef.setInput('questionIndex', 1);
        fixture.componentRef.setInput('reEvaluationInProgress', false);
        questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        addFileSpy = jest.spyOn(component.addNewFile, 'emit');
        removeFileSpy = jest.spyOn(component.removeFile, 'emit');
        fixture.detectChanges();

        const extension = 'png';
        const fileName = 'testFile.' + extension;
        const expectedPath = 'some/client/dependent/path/' + fileName;
        const file = new File([], fileName);

        component.changeToPictureDragItem(component.question().dragItems![1], { target: { files: [file] } });

        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.question().dragItems![0]).toContainAllEntries([
            ['id', 1],
            ['pictureFilePath', undefined],
            ['text', 'Text1'],
        ]);
        expect(component.question().dragItems![2]).toContainAllEntries([
            ['id', 3],
            ['pictureFilePath', undefined],
            ['text', 'Text3'],
        ]);
        expect(component.question().dragItems![1].text).toBeUndefined();
        expect(component.question().dragItems![1].pictureFilePath).toBeDefined();
        expect(component.question().dragItems![1].pictureFilePath).toEndWith('.' + extension);
        const filePath = component.question().dragItems![1].pictureFilePath!;
        expect(component.filePreviewPaths.get(filePath)).toBe(expectedPath);
        expect(addFileSpy).toHaveBeenCalledExactlyOnceWith({ file, fileName: filePath });
        expect(removeFileSpy).not.toHaveBeenCalled();
    });

    it('should change question title', () => {
        const title = 'backupQuestionTitle';
        const q = new DragAndDropQuestion();
        q.title = 'alternativeBackupQuestionTitle';
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.title = title;

        component.resetQuestionTitle();

        expect(component.question().title).toBe(title);
    });

    it('should reset question', () => {
        const title = 'backupQuestionTitle';
        const dropLocation = new DropLocation();
        const item = new DragItem();
        const currentQuestion = new DragAndDropQuestion();
        currentQuestion.title = 'alternativeBackupQuestionTitle';
        fixture.componentRef.setInput('question', currentQuestion);
        fixture.detectChanges();

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

        expect(component.question()).toEqual(backupQuestion);
    });

    it('should reset drag item', () => {
        const firstItem = new DragItem();
        firstItem.id = 404;
        firstItem.invalid = true;
        const secondItem = new DragItem();
        secondItem.id = 404;
        secondItem.invalid = false;
        const q = new DragAndDropQuestion();
        q.dragItems = [new DragItem(), new DragItem(), firstItem, new DragItem()];
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.dragItems = [secondItem];

        component.resetDragItem(firstItem);

        expect(component.question().dragItems![2].invalid).toBeFalse();
    });

    it('should reset drop location', () => {
        const firstItem = new DropLocation();
        firstItem.id = 404;
        firstItem.invalid = true;
        const secondItem = new DropLocation();
        secondItem.id = 404;
        secondItem.invalid = false;
        const q = new DragAndDropQuestion();
        q.dropLocations = [new DropLocation(), new DropLocation(), firstItem, new DropLocation()];
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();
        component.backupQuestion = new DragAndDropQuestion();
        component.backupQuestion.dropLocations = [secondItem];

        component.resetDropLocation(firstItem);

        expect(component.question().dropLocations![2].invalid).toBeFalse();
    });

    it('should toggle preview', () => {
        component.showPreview = true;
        const q = new DragAndDropQuestion();
        q.text = 'should be removed';
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();

        component.togglePreview();

        expect(component.showPreview).toBeFalse();
        expect(component.question().text).toBeUndefined();
    });

    it('should detect changes in markdown and edit accordingly', () => {
        const q = new DragAndDropQuestion();
        q.text = 'should be removed';
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();

        const newValue = 'new value';
        const initialCalls = questionUpdatedSpy.mock.calls.length;
        component.changesInMarkdown(newValue);

        expect(questionUpdatedSpy).toHaveBeenCalledTimes(initialCalls + 1);
        expect(component.question().text).toBeUndefined();
        expect(component.questionEditorText).toBe(newValue);
    });

    it('should detect domain actions', () => {
        const q = new DragAndDropQuestion();
        q.text = 'text';
        q.explanation = 'explanation';
        q.hint = 'hint';
        fixture.componentRef.setInput('question', q);
        fixture.detectChanges();
        let textWithDomainAction: TextWithDomainAction;

        // explanation
        let action = new QuizExplanationAction();
        let text = 'take this as an explanationCommand';
        textWithDomainAction = { text, action };

        component.domainActionsFound([textWithDomainAction]);

        expect(component.question().explanation).toBe(text);

        // hint
        action = new QuizHintAction();
        text = 'take this as a hintCommand';
        textWithDomainAction = { text, action };

        component.domainActionsFound([textWithDomainAction]);

        expect(component.question().hint).toBe(text);

        // text
        text = 'take this null as a command';
        textWithDomainAction = { text, action: undefined };

        component.domainActionsFound([textWithDomainAction]);

        expect(component.question().text).toBe(text);
    });

    it('should get images from drop locations', fakeAsync(() => {
        const q = component.question();
        q.backgroundFilePath = 'bg.png';
        component.filePreviewPaths.set('bg.png', 'data:image/png;base64,test');
        q.dropLocations = [{ posX: 0, posY: 0, width: 50, height: 50 } as DropLocation, { posX: 50, posY: 50, width: 50, height: 50 } as DropLocation];
        q.correctMappings = [];
        q.dragItems = [];

        const mockContext = {
            drawImage: jest.fn(),
            fillStyle: '',
            fillRect: jest.fn(),
        };

        const mockCanvas = {
            getContext: jest.fn().mockReturnValue(mockContext),
            toDataURL: jest.fn().mockReturnValue('data:image/png;base64,cropped'),
            width: 0,
            height: 0,
        } as any;

        const canvasSpy = jest.spyOn(document, 'createElement').mockImplementation((tag) => {
            if (tag === 'canvas') return mockCanvas;
            return null as any;
        });

        const mockImage = {
            onload: () => {},
            src: '',
            height: 200,
            width: 200,
        };

        const imageSpy = jest.spyOn(global, 'Image' as any).mockImplementation(() => {
            const img = mockImage;
            Object.defineProperty(img, 'src', {
                set(value: string) {
                    setTimeout(() => img.onload(), 0);
                },
            });
            return img;
        });

        const createImageDragItemSpy = jest.spyOn(component, 'createImageDragItemFromFile').mockImplementation((file: File) => {
            const di = new DragItem();
            di.pictureFilePath = file.name;
            q.dragItems!.push(di);
            return di;
        });

        const blankOutSpy = jest.spyOn(component, 'blankOutBackgroundImage').mockImplementation(() => {});

        component.getImagesFromDropLocations();

        tick();

        expect(imageSpy).toHaveBeenCalledTimes(2);
        expect(canvasSpy).toHaveBeenCalledTimes(2);
        expect(mockCanvas.toDataURL).toHaveBeenCalledTimes(2);
        expect(createImageDragItemSpy).toHaveBeenCalledTimes(2);
        expect(q.dragItems).toBeArrayOfSize(2);
        expect(q.correctMappings).toBeArrayOfSize(2);
        expect(blankOutSpy).toHaveBeenCalledOnce();
    }));

    it('should blank out background image', fakeAsync(() => {
        const q = component.question();
        q.backgroundFilePath = 'bg.png';
        component.filePreviewPaths.set('bg.png', 'data:image/png;base64,test');
        q.dropLocations = [{ posX: 0, posY: 0, width: 50, height: 50 } as DropLocation, { posX: 50, posY: 50, width: 50, height: 50 } as DropLocation];

        const mockContext = {
            drawImage: jest.fn(),
            fillStyle: '',
            fillRect: jest.fn(),
        };

        const mockCanvas = {
            getContext: jest.fn().mockReturnValue(mockContext),
            toDataURL: jest.fn().mockReturnValue('data:image/png;base64,blanked'),
            width: 0,
            height: 0,
        } as any;

        const canvasSpy = jest.spyOn(document, 'createElement').mockImplementation((tag) => {
            if (tag === 'canvas') return mockCanvas;
            return null as any;
        });

        const mockImage = {
            onload: () => {},
            src: '',
            height: 200,
            width: 200,
        };

        const imageSpy = jest.spyOn(global, 'Image' as any).mockImplementation(() => {
            const img = mockImage;
            Object.defineProperty(img, 'src', {
                set(value: string) {
                    setTimeout(() => img.onload(), 0);
                },
            });
            return img;
        });

        const setBackgroundSpy = jest.spyOn(component, 'setBackgroundFileFromFile').mockImplementation(() => {});

        component.blankOutBackgroundImage();

        tick();

        expect(imageSpy).toHaveBeenCalledOnce();
        expect(canvasSpy).toHaveBeenCalledOnce();
        expect(mockContext.drawImage).toHaveBeenCalledOnce();
        expect(mockContext.fillStyle).toBe('white');
        expect(mockContext.fillRect).toHaveBeenCalledTimes(2);
        expect(mockCanvas.toDataURL).toHaveBeenCalledOnce();
        expect(setBackgroundSpy).toHaveBeenCalledExactlyOnceWith(expect.any(File));
    }));

    it('should convert data url to blob', () => {
        const dataUrl = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==';

        const blob = component.dataUrlToBlob(dataUrl);

        expect(blob).toBeInstanceOf(Blob);
        expect(blob.type).toBe('image/png');
        expect(blob.size).toBeGreaterThan(0);
    });

    it('should convert data url to file', () => {
        const dataUrl = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==';
        const fileName = 'test.png';

        const file = component.dataUrlToFile(dataUrl, fileName);

        expect(file).toBeInstanceOf(File);
        expect(file.name).toBe(fileName);
        expect(file.type).toBe('image/png');
        expect(file.size).toBeGreaterThan(0);
    });
});
