import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { NgStyle } from '@angular/common';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { By } from '@angular/platform-browser';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { metisAnswerPostUser2, metisPostExerciseUser1 } from 'test/helpers/sample/metis-sample-data';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Subject, of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import * as CourseModel from 'app/core/course/shared/entities/course.model';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ChannelReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/channel-reference.action';
import { UserMentionAction } from 'app/shared/monaco-editor/model/actions/communication/user-mention.action';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { ExerciseReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/exercise-reference.action';
import { LectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { FaqReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/faq-reference.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { Overlay, OverlayPositionBuilder } from '@angular/cdk/overlay';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ComponentPortal } from '@angular/cdk/portal';
import { HttpResponse } from '@angular/common/http';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { ListAction } from 'app/shared/monaco-editor/model/actions/list.action';
import monaco from 'monaco-editor';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { FileService } from 'app/shared/service/file.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('PostingsMarkdownEditor', () => {
    let component: PostingMarkdownEditorComponent;
    let fixture: ComponentFixture<PostingMarkdownEditorComponent>;
    let debugElement: DebugElement;
    let mockMarkdownEditorComponent: MarkdownEditorMonacoComponent;
    let metisService: MetisService;
    let fileService: FileService;
    let lectureService: LectureService;
    let findLectureWithDetailsSpy: jest.SpyInstance;

    const backdropClickSubject = new Subject<void>();
    const mockOverlayRef = {
        attach: jest.fn().mockReturnValue({ location: { nativeElement: document.createElement('div') } }),
        backdropClick: jest.fn(() => backdropClickSubject.asObservable()),
        detach: jest.fn(),
        dispose: jest.fn(),
        scrollStrategies: { reposition: jest.fn() },
    };

    const mockOverlay = {
        create: jest.fn().mockReturnValue(mockOverlayRef),
        scrollStrategies: { reposition: jest.fn().mockReturnValue({}) },
    };

    const mockEditor: jest.Mocked<TextEditor> = {
        getPosition: jest.fn(),
        setPosition: jest.fn(),
        focus: jest.fn(),
        addAction: jest.fn(),
        executeAction: jest.fn(),
        layout: jest.fn(),
        replaceTextAtRange: jest.fn(),
        getDomNode: jest.fn(),
        triggerCompletion: jest.fn(),
        getTextAtRange: jest.fn(),
        getLineText: jest.fn(),
        getNumberOfLines: jest.fn(),
        getEndPosition: jest.fn(),
        getSelection: jest.fn(),
        setSelection: jest.fn(),
        revealRange: jest.fn(),
        addCompleter: jest.fn(),
        addPasteListener: jest.fn(),
        getFullText: jest.fn(),
    };

    const mockPositionStrategy = {
        left: jest.fn().mockReturnThis(),
        top: jest.fn().mockReturnThis(),
    };

    const overlayPositionBuilderMock = {
        global: jest.fn().mockReturnValue(mockPositionStrategy),
    };

    beforeEach(() => {
        if (typeof PointerEvent === 'undefined') {
            global.PointerEvent = class PointerEvent extends MouseEvent {
                constructor(type: string, params: MouseEventInit = {}) {
                    super(type, params);
                }
            } as unknown as typeof PointerEvent;
        }
        const mockEmojiSelect = new Subject<{ emoji: any; event: PointerEvent }>();
        const mockComponentRef = {
            instance: {
                emojiSelect: mockEmojiSelect.asObservable(),
            },
            location: { nativeElement: document.createElement('div') },
        };

        mockEditor.getDomNode.mockReturnValue({
            addEventListener: jest.fn(),
        } as any);

        jest.clearAllMocks();
        (ListAction as any).editorsWithListener = new WeakMap<TextEditor, boolean>();

        mockOverlayRef.attach.mockReturnValue(mockComponentRef);

        return TestBed.configureTestingModule({
            imports: [PostingMarkdownEditorComponent, MockComponent(MarkdownEditorMonacoComponent), NgStyle],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: FileService, useClass: MockFileService },
                MockProvider(LectureService),
                MockProvider(CourseManagementService),
                MockProvider(ChannelService),
                MockProvider(AlertService),
                MockProvider(TranslateService),
                { provide: Overlay, useValue: mockOverlay },
                { provide: OverlayPositionBuilder, useValue: overlayPositionBuilderMock },
                { provide: MarkdownEditorMonacoComponent, useValue: mockMarkdownEditorComponent },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            declarations: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingMarkdownEditorComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                fileService = TestBed.inject(FileService);
                metisService = TestBed.inject(MetisService);
                lectureService = TestBed.inject(LectureService);

                findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findAllByCourseIdWithSlides');
                const returnValue = of(new HttpResponse({ body: [], status: 200 }));
                findLectureWithDetailsSpy.mockReturnValue(returnValue);
                fixture.autoDetectChanges();
                mockMarkdownEditorComponent = fixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
                component.ngOnInit();
                component.content = metisPostExerciseUser1.content;

                mockEmojiSelect.next({ emoji: { native: '😀' }, event: new PointerEvent('click') });
            });
    });

    it('should have set the correct default commands on init if messaging or communication is enabled', () => {
        component.ngOnInit();
        containDefaultActions(component.defaultActions);
        expect(component.defaultActions).toEqual(expect.arrayContaining([expect.any(UserMentionAction), expect.any(ChannelReferenceAction)]));
        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService, fileService));
    });

    it('should have set the correct default commands on init if communication is disabled', () => {
        jest.spyOn(CourseModel, 'isCommunicationEnabled').mockReturnValueOnce(false);
        component.ngOnInit();
        containDefaultActions(component.defaultActions);
        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService, fileService));
    });

    function containDefaultActions(defaultActions: TextEditorAction[]) {
        expect(defaultActions).toEqual(
            expect.arrayContaining([
                expect.any(BoldAction),
                expect.any(ItalicAction),
                expect.any(UnderlineAction),
                expect.any(QuoteAction),
                expect.any(CodeAction),
                expect.any(CodeBlockAction),
                expect.any(EmojiAction),
                expect.any(UrlAction),
                expect.any(AttachmentAction),
                expect.any(ExerciseReferenceAction),
            ]),
        );
    }

    it('should have set the correct default commands on init if faq is enabled', () => {
        jest.spyOn(CourseModel, 'isFaqEnabled').mockReturnValueOnce(true);
        component.ngOnInit();
        containDefaultActions(component.defaultActions);
        expect(component.defaultActions).toEqual(expect.arrayContaining([expect.any(FaqReferenceAction)]));
        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService, fileService));
    });

    it('should have set the correct default commands on init if faq is disabled', () => {
        jest.spyOn(CourseModel, 'isFaqEnabled').mockReturnValueOnce(false);
        component.ngOnInit();
        containDefaultActions(component.defaultActions);
        expect(component.defaultActions).toEqual(expect.not.arrayContaining([expect.any(FaqReferenceAction)]));
        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService, fileService));
    });

    it('should show the correct amount of characters below the markdown input', () => {
        component.maxContentLength = 200;
        fixture.changeDetectorRef.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).not.toBe('red');
    });

    it('should show the correct amount of characters in red if max length exceeded', () => {
        component.maxContentLength = 5;
        fixture.changeDetectorRef.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).toBe('red');
    });

    it('should initialize markdown correctly with post content', () => {
        component.maxContentLength = 200;
        fixture.changeDetectorRef.detectChanges();
        expect(mockMarkdownEditorComponent.markdown).toEqual(component.content);
    });

    it('should update value if markdown change is emitted', () => {
        component.maxContentLength = 200;
        fixture.changeDetectorRef.detectChanges();
        mockMarkdownEditorComponent.markdownChange.emit('updated text');
        expect(component.content).toBe('updated text');
    });

    it('should write value of form group in content variable', () => {
        component.writeValue(metisAnswerPostUser2);
        expect(component.content).toEqual(metisAnswerPostUser2);
    });

    it('should write an empty string into content for undefined values', () => {
        component.writeValue(undefined);
        expect(component.content).toBe('');
    });

    it('should register onChange', () => {
        const onChange = jest.fn();
        component.registerOnChange(onChange);
        expect(component.onChange).toBe(onChange);
    });

    it('should call preventDefault when the user presses enter', () => {
        component.suppressNewlineOnEnter = true;
        const event = new KeyboardEvent('keydown', { key: 'Enter' });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        component.onKeyDown(event);
        expect(preventDefaultSpy).toHaveBeenCalledOnce();
    });

    it('should not call preventDefault when the user presses shift+enter', () => {
        component.suppressNewlineOnEnter = true;
        const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        component.onKeyDown(event);
        expect(preventDefaultSpy).not.toHaveBeenCalled();
    });

    it('should not suppress newlines on enter if disabled', () => {
        component.suppressNewlineOnEnter = false;
        const event = new KeyboardEvent('keydown', { key: 'Enter' });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        component.onKeyDown(event);
        expect(preventDefaultSpy).not.toHaveBeenCalled();
    });

    it('should not create emoji picker if position is not set', () => {
        const emojiAction = new EmojiAction(component.viewContainerRef, mockOverlay as any, overlayPositionBuilderMock as any);
        emojiAction.run(mockEditor);

        expect(mockOverlay.create).not.toHaveBeenCalled();
        expect(mockOverlayRef.attach).not.toHaveBeenCalled();
    });

    it('should attach EmojiPickerComponent to overlay when EmojiAction.run is called', () => {
        const emojiAction = component.defaultActions.find((action) => action instanceof EmojiAction) as EmojiAction;
        emojiAction.setPoint({ x: 100, y: 200 });

        emojiAction.run(mockEditor);

        expect(mockOverlayRef.attach).toHaveBeenCalledWith(expect.any(ComponentPortal));
        expect(mockOverlayRef.backdropClick).toHaveBeenCalled();
    });

    it('should create overlay with correct position when EmojiAction.run is called', () => {
        const emojiAction = component.defaultActions.find((action) => action instanceof EmojiAction) as EmojiAction;
        emojiAction.setPoint({ x: 100, y: 200 });

        const mockPositionStrategy = {
            left: jest.fn().mockReturnThis(),
            top: jest.fn().mockReturnThis(),
        };

        mockOverlay.create.mockReturnValue(mockOverlayRef);
        overlayPositionBuilderMock.global.mockReturnValue(mockPositionStrategy);

        emojiAction.run(mockEditor);

        expect(mockOverlay.create).toHaveBeenCalledWith({
            positionStrategy: mockPositionStrategy,
            hasBackdrop: true,
            backdropClass: 'cdk-overlay-transparent-backdrop',
            scrollStrategy: mockOverlay.scrollStrategies.reposition(),
            width: '0',
        });

        expect(mockPositionStrategy.left).toHaveBeenCalledWith(`85px`);
        expect(mockPositionStrategy.top).toHaveBeenCalledWith(`185px`);
    });

    it('should detach overlay and close EmojiPickerComponent on backdrop click', () => {
        const emojiAction = component.defaultActions.find((action) => action instanceof EmojiAction) as EmojiAction;
        emojiAction.setPoint({ x: 100, y: 200 });

        emojiAction.run(mockEditor);
        backdropClickSubject.next();

        expect(mockOverlayRef.dispose).toHaveBeenCalled();
    });

    it('should destroy emoji picker if it is already open', () => {
        const emojiAction = new EmojiAction(component.viewContainerRef, mockOverlay as any, overlayPositionBuilderMock as any);

        emojiAction['overlayRef'] = mockOverlayRef as any;
        const destroySpy = jest.spyOn(emojiAction as any, 'destroyEmojiPicker');

        emojiAction.run(mockEditor);

        expect(destroySpy).toHaveBeenCalled();
        expect(mockOverlayRef.dispose).toHaveBeenCalled();
    });

    it('should clean up overlay reference on destroy', () => {
        const emojiAction = new EmojiAction(component.viewContainerRef, mockOverlay as any, overlayPositionBuilderMock as any);

        emojiAction['overlayRef'] = mockOverlayRef as any;
        emojiAction['destroyEmojiPicker']();

        expect(mockOverlayRef.dispose).toHaveBeenCalled();
        expect(emojiAction['overlayRef']).toBeUndefined();
    });

    const simulateKeydownEvent = (key: string, modifiers: { shiftKey?: boolean; metaKey?: boolean } = {}) => {
        const event = new KeyboardEvent('keydown', { key, ...modifiers });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        const stopPropagationSpy = jest.spyOn(event, 'stopPropagation');

        const addEventListenerMock = (mockEditor.getDomNode()?.addEventListener as jest.Mock).mock;
        const keydownListener = addEventListenerMock.calls.find((call: any) => call[0] === 'keydown')[1];
        keydownListener(event);

        return { event, preventDefaultSpy, stopPropagationSpy };
    };

    it('should handle Shift+Enter correctly by inserting a single line break with prefix', () => {
        const bulletedListAction = component.defaultActions.find((action) => action instanceof BulletedListAction) as BulletedListAction;

        mockEditor.getPosition.mockReturnValue({
            getLineNumber: () => 1,
            getColumn: () => 6,
        } as TextEditorPosition);
        mockEditor.getLineText.mockReturnValue('- First line');

        bulletedListAction.run(mockEditor);

        const { preventDefaultSpy } = simulateKeydownEvent('Enter', { shiftKey: true });

        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expect.any(TextEditorRange), '\n- ');
        expect(mockEditor.setPosition).toHaveBeenCalledWith(new TextEditorPosition(2, 3));
    });

    it('should handle Cmd+Enter correctly without inserting double line breaks', () => {
        const bulletedListAction = component.defaultActions.find((action) => action instanceof BulletedListAction) as BulletedListAction;

        mockEditor.getPosition.mockReturnValue({
            getLineNumber: () => 1,
            getColumn: () => 6,
        } as TextEditorPosition);
        mockEditor.getLineText.mockReturnValue('- First line');

        bulletedListAction.run(mockEditor);

        const { preventDefaultSpy, stopPropagationSpy } = simulateKeydownEvent('Enter', { metaKey: true });

        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(stopPropagationSpy).toHaveBeenCalled();
        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expect.any(TextEditorRange), '\n- ');
        expect(mockEditor.setPosition).toHaveBeenCalledWith(new TextEditorPosition(2, 3));
    });

    const simulateListAction = (action: TextEditorAction, selectedText: string, expectedText: string, startLineNumber: number = 1) => {
        const lines = selectedText.split('\n');

        mockEditor.getTextAtRange.mockReturnValue(selectedText);

        mockEditor.getLineText.mockImplementation((lineNumber: number) => {
            const index = lineNumber - startLineNumber;
            return lines[index] || '';
        });

        mockEditor.getPosition.mockReturnValue({
            getLineNumber: () => startLineNumber,
            getColumn: () => 1,
        } as TextEditorPosition);

        const endLineNumber = startLineNumber + lines.length - 1;
        mockEditor.getSelection.mockReturnValue(
            new TextEditorRange(new TextEditorPosition(startLineNumber, 1), new TextEditorPosition(endLineNumber, lines[lines.length - 1].length + 1)),
        );

        action.run(mockEditor);

        const replaceCalls = mockEditor.replaceTextAtRange.mock.calls;
        expect(replaceCalls).toHaveLength(1);

        const [range, text] = replaceCalls[0];

        expect(range).toEqual(
            expect.objectContaining({
                startPosition: expect.objectContaining({
                    lineNumber: expect.any(Number),
                    column: expect.any(Number),
                }),
                endPosition: expect.objectContaining({
                    lineNumber: expect.any(Number),
                    column: expect.any(Number),
                }),
            }),
        );

        expect(text).toBe(expectedText);
    };

    it('should add bulleted list prefixes correctly', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const selectedText = `First line\nSecond line\nThird line`;
        const expectedText = `- First line\n- Second line\n- Third line`;

        simulateListAction(bulletedListAction, selectedText, expectedText);
    });

    it('should remove bulleted list prefixes correctly when toggled', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const selectedText = `- First line\n- Second line\n- Third line`;
        const expectedText = `First line\nSecond line\nThird line`;

        simulateListAction(bulletedListAction, selectedText, expectedText);
    });

    it('should add ordered list prefixes correctly starting from 1', () => {
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;
        const selectedText = `First line\nSecond line\nThird line`;
        const expectedText = `1. First line\n2. Second line\n3. Third line`;

        simulateListAction(orderedListAction, selectedText, expectedText);
    });

    it('should remove ordered list prefixes correctly when toggled', () => {
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;
        const selectedText = `1.  First line\n2.  Second line\n3.  Third line`;
        const expectedText = `First line\nSecond line\nThird line`;

        simulateListAction(orderedListAction, selectedText, expectedText);
    });

    it('should switch from bulleted list to ordered list correctly', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;
        const bulletedText = `- First line\n- Second line\n- Third line`;
        const expectedOrderedText = `1. First line\n2. Second line\n3. Third line`;

        simulateListAction(bulletedListAction, bulletedText, `First line\nSecond line\nThird line`);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(orderedListAction, `First line\nSecond line\nThird line`, expectedOrderedText);
    });

    it('should switch from ordered list to bulleted list correctly', () => {
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const orderedText = `1.  First line\n2.  Second line\n3.  Third line`;
        const expectedBulletedText = `- First line\n- Second line\n- Third line`;

        simulateListAction(orderedListAction, orderedText, `First line\nSecond line\nThird line`);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(bulletedListAction, `First line\nSecond line\nThird line`, expectedBulletedText);
    });

    it('should start ordered list numbering from 1 regardless of an inline list', () => {
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;
        const selectedText = `Some previous text\n1.  First line\n2.  Second line\n3.  Third line`;
        const expectedText = `1. Some previous text\n2. First line\n3. Second line\n4. Third line`;

        simulateListAction(orderedListAction, selectedText, expectedText);
    });

    it('should update prefixes correctly when switching list types', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;

        const bulletedText = `- First line\n- Second line\n- Third line`;
        const expectedOrderedText = `1. First line\n2. Second line\n3. Third line`;

        simulateListAction(bulletedListAction, `First line\nSecond line\nThird line`, bulletedText);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(orderedListAction, bulletedText, expectedOrderedText);
    });

    it('should toggle list prefixes correctly when pressing the same list button twice', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;

        const initialText = `First line\nSecond line\nThird line`;

        const bulletedText = `- First line\n- Second line\n- Third line`;
        simulateListAction(bulletedListAction, initialText, bulletedText);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(bulletedListAction, bulletedText, initialText);

        const orderedText = `1. First line\n2. Second line\n3. Third line`;
        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(orderedListAction, initialText, orderedText);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(orderedListAction, orderedText, initialText);
    });

    it('should handle pressing different list buttons correctly', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        const orderedListAction = component.defaultActions.find((action: any) => action instanceof OrderedListAction) as OrderedListAction;

        const initialText = `First line\nSecond line\nThird line`;

        const bulletedText = `- First line\n- Second line\n- Third line`;
        simulateListAction(bulletedListAction, initialText, bulletedText);

        const orderedText = `1. First line\n2. Second line\n3. Third line`;
        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(orderedListAction, bulletedText, orderedText);

        mockEditor.replaceTextAtRange.mockClear();
        simulateListAction(bulletedListAction, orderedText, bulletedText);
    });

    it('should handle key down event and invoke the correct action', () => {
        const bulletedListAction = new BulletedListAction();

        component.defaultActions = [bulletedListAction];

        const handleActionClickSpy = jest.spyOn(component.markdownEditor, 'handleActionClick');

        const mockModel = {
            getLineContent: jest.fn().mockReturnValue('- List item'),
        } as unknown as monaco.editor.ITextModel;
        const mockPosition = { lineNumber: 1 } as monaco.Position;

        (component as any).handleKeyDown(mockModel, mockPosition.lineNumber);

        expect(handleActionClickSpy).toHaveBeenCalledWith(expect.any(MouseEvent), bulletedListAction);
    });

    it('should handle invalid line content gracefully', () => {
        const mockModel = {
            getLineContent: jest.fn().mockReturnValue(''),
        } as unknown as monaco.editor.ITextModel;
        const mockPosition = { lineNumber: 1 } as monaco.Position;
        const handleActionClickSpy = jest.spyOn(component.markdownEditor, 'handleActionClick');

        (component as any).handleKeyDown(mockModel, mockPosition.lineNumber);
        expect(handleActionClickSpy).not.toHaveBeenCalled();
    });

    it('should keep the cursor position intact when editing text in a list item', () => {
        const bulletedListAction = component.defaultActions.find((action: any) => action instanceof BulletedListAction) as BulletedListAction;
        mockEditor.getPosition.mockReturnValue({
            getLineNumber: () => 1,
            getColumn: () => 5,
        } as TextEditorPosition);
        mockEditor.getLineText.mockReturnValue('- First line');
        mockEditor.getTextAtRange.mockReturnValue('');

        const replaceTextSpy = jest.spyOn(mockEditor, 'replaceTextAtRange');
        bulletedListAction.run(mockEditor);

        expect(replaceTextSpy).not.toHaveBeenCalled();

        const cursorPosition = mockEditor.getPosition();
        expect(cursorPosition).toEqual({
            getLineNumber: expect.any(Function),
            getColumn: expect.any(Function),
        });
        expect(cursorPosition?.getColumn()).toBe(5);
    });

    it('should insert emoji at the cursor position', () => {
        const emojiAction = new EmojiAction(component.viewContainerRef, mockOverlay as any, overlayPositionBuilderMock as any);
        const mockCursorPosition = new TextEditorPosition(1, 5);
        mockEditor.getPosition.mockReturnValue(mockCursorPosition);

        emojiAction.insertEmojiAtCursor(mockEditor, '😀');

        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expect.any(TextEditorRange), '😀');
        expect(mockEditor.setPosition).toHaveBeenCalledWith(new TextEditorPosition(1, 7));
        expect(mockEditor.focus).toHaveBeenCalled();
    });

    it('should close the emoji picker and insert emoji on selection event', () => {
        const emojiAction = new EmojiAction(component.viewContainerRef, mockOverlay as any, overlayPositionBuilderMock as any);
        emojiAction.setPoint({ x: 100, y: 200 });
        mockEditor.getPosition.mockReturnValue(new TextEditorPosition(1, 1));

        const emojiSelectSubject = new Subject<{ emoji: any; event: PointerEvent }>();
        const componentRef = {
            instance: {
                emojiSelect: emojiSelectSubject.asObservable(),
            },
            location: { nativeElement: document.createElement('div') },
        };

        mockOverlayRef.attach.mockReturnValue(componentRef);
        emojiAction.run(mockEditor);

        const selectionEvent = { emoji: { native: '😀' }, event: new PointerEvent('click') };
        emojiSelectSubject.next(selectionEvent);

        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expect.any(TextEditorRange), '😀');
        expect(mockOverlayRef.dispose).toHaveBeenCalled();
    });
});
