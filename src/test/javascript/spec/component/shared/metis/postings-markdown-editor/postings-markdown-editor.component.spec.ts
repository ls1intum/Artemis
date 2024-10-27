import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { getElement } from '../../../../helpers/utils/general.utils';
import { By } from '@angular/platform-browser';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { metisAnswerPostUser2, metisPostExerciseUser1 } from '../../../../helpers/sample/metis-sample-data';
import { LectureService } from 'app/lecture/lecture.service';
import { Subject } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import * as CourseModel from 'app/entities/course.model';
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
import { UrlAction } from '../../../../../../../main/webapp/app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from '../../../../../../../main/webapp/app/shared/monaco-editor/model/actions/attachment.action';
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { Overlay, OverlayPositionBuilder } from '@angular/cdk/overlay';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { ComponentPortal } from '@angular/cdk/portal';

describe('PostingsMarkdownEditor', () => {
    let component: PostingMarkdownEditorComponent;
    let fixture: ComponentFixture<PostingMarkdownEditorComponent>;
    let debugElement: DebugElement;
    let mockMarkdownEditorComponent: MarkdownEditorMonacoComponent;
    let metisService: MetisService;
    let lectureService: LectureService;

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

    const mockEditor: TextEditor = {
        getPosition: jest.fn(),
        setPosition: jest.fn(),
        focus: jest.fn(),
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

        mockOverlayRef.attach.mockReturnValue(mockComponentRef);

        return TestBed.configureTestingModule({
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                MockProvider(LectureService),
                MockProvider(CourseManagementService),
                MockProvider(ChannelService),
                { provide: Overlay, useValue: mockOverlay },
                { provide: OverlayPositionBuilder, useValue: overlayPositionBuilderMock },
            ],
            declarations: [PostingMarkdownEditorComponent, MockComponent(MarkdownEditorMonacoComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingMarkdownEditorComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisService = TestBed.inject(MetisService);
                lectureService = TestBed.inject(LectureService);
                fixture.autoDetectChanges();
                mockMarkdownEditorComponent = fixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
                component.ngOnInit();
                component.content = metisPostExerciseUser1.content;

                mockEmojiSelect.next({ emoji: { native: '😀' }, event: new PointerEvent('click') });
            });
    });

    it('should have set the correct default commands on init if messaging or communication is enabled', () => {
        component.ngOnInit();

        expect(component.defaultActions).toEqual(
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
                expect.any(UserMentionAction),
                expect.any(ChannelReferenceAction),
                expect.any(ExerciseReferenceAction),
            ]),
        );

        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService));
    });

    it('should have set the correct default commands on init if communication is disabled', () => {
        jest.spyOn(CourseModel, 'isCommunicationEnabled').mockReturnValueOnce(false);
        component.ngOnInit();

        expect(component.defaultActions).toEqual(
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

        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService));
    });

    it('should show the correct amount of characters below the markdown input', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).not.toBe('red');
    });

    it('should show the correct amount of characters in red if max length exceeded', () => {
        component.maxContentLength = 5;
        fixture.detectChanges();
        const charCounter = getElement(debugElement, 'p.small');
        expect(charCounter.textContent).toContain(component.maxContentLength.toString());
        expect(charCounter.textContent).toContain(metisPostExerciseUser1.content!.length.toString());
        expect(charCounter.style.color).toBe('red');
    });

    it('should initialize markdown correctly with post content', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
        expect(mockMarkdownEditorComponent.markdown).toEqual(component.content);
    });

    it('should update value if markdown change is emitted', () => {
        component.maxContentLength = 200;
        fixture.detectChanges();
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
        expect(emojiAction['overlayRef']).toBeNull();
    });
});
