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
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
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

describe('PostingsMarkdownEditor', () => {
    let component: PostingMarkdownEditorComponent;
    let fixture: ComponentFixture<PostingMarkdownEditorComponent>;
    let debugElement: DebugElement;
    let mockMarkdownEditorComponent: MarkdownEditorMonacoComponent;
    let metisService: MetisService;
    let courseManagementService: CourseManagementService;
    let channelService: ChannelService;
    let lectureService: LectureService;
    let findLectureWithDetailsSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }, MockProvider(LectureService), MockProvider(CourseManagementService), MockProvider(ChannelService)],
            declarations: [PostingMarkdownEditorComponent, MockComponent(MarkdownEditorMonacoComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingMarkdownEditorComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisService = TestBed.inject(MetisService);
                courseManagementService = TestBed.inject(CourseManagementService);
                lectureService = TestBed.inject(LectureService);
                channelService = TestBed.inject(ChannelService);
                findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findAllByCourseIdWithSlides');
                const returnValue = of(new HttpResponse({ body: [], status: 200 }));
                findLectureWithDetailsSpy.mockReturnValue(returnValue);
                fixture.autoDetectChanges();
                mockMarkdownEditorComponent = fixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
                component.ngOnInit();
                component.content = metisPostExerciseUser1.content;
            });
    });

    it('should have set the correct default commands on init if messaging or communication is enabled', () => {
        component.ngOnInit();

        expect(component.defaultActions).toEqual([
            new BoldAction(),
            new ItalicAction(),
            new UnderlineAction(),
            new QuoteAction(),
            new CodeAction(),
            new CodeBlockAction(),
            new UserMentionAction(courseManagementService, metisService),
            new ChannelReferenceAction(metisService, channelService),
            new ExerciseReferenceAction(metisService),
        ]);

        expect(component.lectureAttachmentReferenceAction).toEqual(new LectureAttachmentReferenceAction(metisService, lectureService));
    });

    it('should have set the correct default commands on init if communication and messaging and communication is disabled', () => {
        jest.spyOn(CourseModel, 'isCommunicationEnabled').mockReturnValueOnce(false);
        component.ngOnInit();

        expect(component.defaultActions).toEqual([
            new BoldAction(),
            new ItalicAction(),
            new UnderlineAction(),
            new QuoteAction(),
            new CodeAction(),
            new CodeBlockAction(),
            new ExerciseReferenceAction(metisService),
        ]);

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
});
