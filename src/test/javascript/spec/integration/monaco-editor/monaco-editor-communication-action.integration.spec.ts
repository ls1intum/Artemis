import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { CommunicationService } from 'app/communication/service/communication.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { ChannelReferenceAction } from 'app/editor/monaco-editor/model/actions/communication/channel-reference.action';
import { UserMentionAction } from 'app/editor/monaco-editor/model/actions/communication/user-mention.action';
import { ExerciseReferenceAction } from 'app/editor/monaco-editor/model/actions/communication/exercise-reference.action';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import {
    communicationExamChannelDTO,
    communicationExerciseChannelDTO,
    communicationGeneralChannelDTO,
    communicationTutor,
    communicationUser1,
    communicationUser2,
} from 'test/helpers/sample/communication-sample-data';
import { TextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import * as monaco from 'monaco-editor';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { User } from 'app/account/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureAttachmentReferenceAction } from 'app/editor/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ReferenceType } from 'app/communication/communication.util';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { FaqReferenceAction } from 'app/editor/monaco-editor/model/actions/communication/faq-reference.action';
import { Faq } from 'app/communication/shared/entities/faq.model';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { FileService } from 'app/foundation/service/file.service';
import { ChannelDTO, ChannelIdAndNameDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('MonacoEditorCommunicationActionIntegration', () => {
    let comp: MonacoEditorComponent;
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let communicationService: CommunicationService;
    let fileService: FileService;
    let courseManagementService: CourseManagementService;
    let channelService: ChannelService;
    let lectureService: LectureService;
    let provider: monaco.languages.CompletionItemProvider;

    // Actions
    let channelReferenceAction: ChannelReferenceAction;
    let userMentionAction: UserMentionAction;
    let exerciseReferenceAction: ExerciseReferenceAction;
    let exerciseService: ExerciseService;
    let faqReferenceAction: FaqReferenceAction;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: CommunicationService, useClass: MockCommunicationService },
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LectureService),
                MockProvider(ExerciseService),
                MockProvider(CourseManagementService),
                MockProvider(ChannelService),
            ],
        }).compileComponents();

        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        fixture = TestBed.createComponent(MonacoEditorComponent);
        comp = fixture.componentInstance;
        communicationService = TestBed.inject(CommunicationService);
        fileService = TestBed.inject(FileService);
        courseManagementService = TestBed.inject(CourseManagementService);
        lectureService = TestBed.inject(LectureService);
        channelService = TestBed.inject(ChannelService);
        channelReferenceAction = new ChannelReferenceAction(communicationService, channelService);
        userMentionAction = new UserMentionAction(courseManagementService, communicationService);
        exerciseService = TestBed.inject(ExerciseService);
        // The action asks for the exercise titles instead of reading them off the course, which no longer carries them
        vi.spyOn(exerciseService, 'getTitlesForCourse').mockReturnValue(
            of((communicationService.getCourse().exercises ?? []).map((exercise) => ({ id: exercise.id!, title: exercise.title, type: exercise.type }))),
        );
        exerciseReferenceAction = new ExerciseReferenceAction(communicationService, exerciseService);
        faqReferenceAction = new FaqReferenceAction(communicationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const registerActionWithCompletionProvider = (action: TextEditorAction, triggerCharacter?: string) => {
        const registerCompletionProviderStub = vi.spyOn(monaco.languages, 'registerCompletionItemProvider').mockImplementation(() => ({ dispose: () => {} }));
        comp.registerAction(action);
        expect(registerCompletionProviderStub).toHaveBeenCalledOnce();
        provider = registerCompletionProviderStub.mock.calls[0][1];
        expect(provider).toBeDefined();
        expect(provider.provideCompletionItems).toBeDefined();
        if (triggerCharacter) {
            expect(provider.triggerCharacters).toContain(triggerCharacter);
        }
    };

    describe.each([
        { actionId: ChannelReferenceAction.ID, defaultInsertText: '#', triggerCharacter: '#' },
        { actionId: UserMentionAction.ID, defaultInsertText: '@', triggerCharacter: '@' },
        { actionId: ExerciseReferenceAction.ID, defaultInsertText: '/exercise', triggerCharacter: '/' },
        { actionId: FaqReferenceAction.ID, defaultInsertText: '/faq', triggerCharacter: '/' },
    ])('Suggestions and default behavior for $actionId', ({ actionId, defaultInsertText, triggerCharacter }) => {
        let action: ChannelReferenceAction | UserMentionAction | ExerciseReferenceAction | FaqReferenceAction;
        let channels: ChannelIdAndNameDTO[];
        let users: User[];
        let exercises: Exercise[];
        let faqs: Faq[];

        beforeEach(async () => {
            fixture.detectChanges();
            comp.changeModel('initial', '');
            channels = [communicationGeneralChannelDTO, communicationExamChannelDTO, communicationExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            users = [communicationUser1, communicationUser2, communicationTutor];
            vi.spyOn(courseManagementService, 'searchMembersForUserMentions').mockReturnValue(of(new HttpResponse({ body: users, status: 200 })));
            exercises = communicationService.getCourse().exercises!;
            faqs = await firstValueFrom(communicationService.getFaqs());

            switch (actionId) {
                case ChannelReferenceAction.ID:
                    action = channelReferenceAction;
                    break;
                case UserMentionAction.ID:
                    action = userMentionAction;
                    break;
                case ExerciseReferenceAction.ID:
                    action = exerciseReferenceAction;
                    break;
                case FaqReferenceAction.ID:
                    action = faqReferenceAction;
                    break;
            }
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should suggest no values for the wrong model', async () => {
            registerActionWithCompletionProvider(action, triggerCharacter);
            comp.changeModel('other', '#ch');
            const suggestions = await provider.provideCompletionItems(comp.models[1], new monaco.Position(1, 4), {} as any, {} as any);
            expect(suggestions).toBeUndefined();
        });

        it('should suggest no values if the user is not typing a reference', async () => {
            comp.setText('some text that is no reference');
            registerActionWithCompletionProvider(action, triggerCharacter);
            const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, 4), {} as any, {} as any);
            expect(providerResult).toBeUndefined();
        });

        it('should insert the correct default text when executed', () => {
            registerActionWithCompletionProvider(action, triggerCharacter);
            action.executeInCurrentEditor();
            expect(comp.getText()).toBe(defaultInsertText);
        });

        const checkChannelSuggestions = (suggestions: monaco.languages.CompletionItem[], channels: ChannelIdAndNameDTO[]) => {
            expect(suggestions).toHaveLength(channels.length);
            suggestions.forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`#${channels[index].name}`);
                expect(suggestion.insertText).toBe(`[channel]${channels[index].name}(${channels[index].id})[/channel]`);
                expect(suggestion.detail).toBe(action.label);
            });
        };

        const checkUserSuggestions = (suggestions: monaco.languages.CompletionItem[], users: User[]) => {
            expect(suggestions).toHaveLength(users.length);
            suggestions.forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`@${users[index].name}`);
                expect(suggestion.insertText).toBe(`[user]${users[index].name}(${users[index].login})[/user]`);
                expect(suggestion.detail).toBe(action.label);
            });
        };

        const checkExerciseSuggestions = (suggestions: monaco.languages.CompletionItem[], exercises: Exercise[]) => {
            expect(suggestions).toHaveLength(exercises.length);
            suggestions.forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`/exercise ${exercises[index].title}`);
                expect(suggestion.insertText).toBe(
                    `[${exercises[index].type}]${exercises[index].title}(${communicationService.getLinkForExercise(exercises[index].id!.toString())})[/${exercises[index].type}]`,
                );
                expect(suggestion.detail).toBe(exercises[index].type);
            });
        };

        const checkFaqSuggestions = (suggestions: monaco.languages.CompletionItem[], faqs: Faq[]) => {
            expect(suggestions).toHaveLength(faqs.length);
            suggestions.forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`/faq ${faqs[index].questionTitle}`);
                expect(suggestion.insertText).toBe(`[faq]${faqs[index].questionTitle}(${communicationService.getLinkForFaq()}?faqId=${faqs[index].id})[/faq]`);
                expect(suggestion.detail).toBe('faq');
            });
        };

        it.each(['', 'ex'])('should suggest the correct values if the user is typing a reference (suffix "%s")', async (referenceSuffix: string) => {
            const reference = triggerCharacter + referenceSuffix;
            comp.setText(reference);
            const column = reference.length + 1;
            registerActionWithCompletionProvider(action, triggerCharacter);
            const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, column), {} as any, {} as any);
            expect(providerResult).toBeDefined();
            expect(providerResult!.incomplete).toBe(actionId === UserMentionAction.ID);
            const suggestions = providerResult!.suggestions;
            switch (actionId) {
                case ChannelReferenceAction.ID:
                    checkChannelSuggestions(suggestions, channels);
                    break;
                case UserMentionAction.ID:
                    checkUserSuggestions(suggestions, users);
                    break;
                case ExerciseReferenceAction.ID:
                    checkExerciseSuggestions(suggestions, exercises);
                    break;
                case FaqReferenceAction.ID:
                    checkFaqSuggestions(suggestions, faqs);
                    break;
            }
        });
    });

    describe('ChannelReferenceAction', () => {
        it('should use cached channels if available', async () => {
            const channels: ChannelIdAndNameDTO[] = [communicationGeneralChannelDTO, communicationExamChannelDTO, communicationExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            const getChannelsSpy = vi.spyOn(channelService, 'getPublicChannelsOfCourse');
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.fetchChannels()).toBe(channels);
            expect(getChannelsSpy).not.toHaveBeenCalled();
        });

        it('should load and cache channels if none are cached', async () => {
            const channels: ChannelIdAndNameDTO[] = [communicationGeneralChannelDTO, communicationExamChannelDTO, communicationExerciseChannelDTO];
            const getChannelsStub = vi.spyOn(channelService, 'getPublicChannelsOfCourse').mockReturnValue(of(new HttpResponse({ body: channels, status: 200 })));
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.fetchChannels()).toBe(channels);
            expect(getChannelsStub).toHaveBeenCalledExactlyOnceWith(communicationService.getCourse().id!);
            expect(channelReferenceAction.cachedChannels).toBe(channels);
        });

        it('should insert # for channel references', () => {
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            channelReferenceAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('#');
        });
    });

    describe('UserMentionAction @all suggestion', () => {
        let users: User[];

        const suggestMentions = async (text: string, action: UserMentionAction = userMentionAction) => {
            comp.setText(text);
            registerActionWithCompletionProvider(action, '@');
            const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, text.length + 1), {} as any, {} as any);
            expect(providerResult).toBeDefined();
            return providerResult!.suggestions;
        };

        beforeEach(() => {
            fixture.detectChanges();
            comp.changeModel('initial', '');
            users = [communicationUser1, communicationUser2, communicationTutor];
            vi.spyOn(courseManagementService, 'searchMembersForUserMentions').mockReturnValue(of(new HttpResponse({ body: users, status: 200 })));
        });

        it('should suggest @all as the first suggestion in a group chat', async () => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new GroupChatDTO());

            const suggestions = await suggestMentions('@');

            expect(suggestions).toHaveLength(users.length + 1);
            expect(suggestions[0].label).toBe('@all');
            expect(suggestions[0].insertText).toBe('@all ');
            expect(suggestions[0].sortText! < suggestions[1].label.toString()).toBe(true);
            // the users follow the synthetic suggestion and are inserted as user mentions
            suggestions.slice(1).forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`@${users[index].name}`);
                expect(suggestion.insertText).toBe(`[user]${users[index].name}(${users[index].login})[/user]`);
            });
        });

        it('should suggest @all for a partially typed token in a group chat', async () => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new GroupChatDTO());

            const suggestions = await suggestMentions('@al');

            expect(suggestions[0].label).toBe('@all');
            expect(suggestions[0].insertText).toBe('@all ');
        });

        it('should not suggest @all while editing a posting, because editing notifies nobody', async () => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new GroupChatDTO());
            const editingAction = new UserMentionAction(courseManagementService, communicationService, () => true);

            const suggestions = await suggestMentions('@', editingAction);

            expect(suggestions).toHaveLength(users.length);
            expect(suggestions.map((suggestion) => suggestion.label)).not.toContain('@all');
        });

        it('should prefer the conversation of the posting over the current conversation', async () => {
            // e.g. a thread opened from the view with all messages, where the current conversation is not the one of the posting
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new ChannelDTO());
            const replyAction = new UserMentionAction(
                courseManagementService,
                communicationService,
                () => false,
                () => new GroupChatDTO(),
            );

            const suggestions = await suggestMentions('@', replyAction);

            expect(suggestions).toHaveLength(users.length + 1);
            expect(suggestions[0].label).toBe('@all');
        });

        it('should not suggest @all if the conversation of the posting is no group chat, although the current conversation is one', async () => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new GroupChatDTO());
            const replyAction = new UserMentionAction(
                courseManagementService,
                communicationService,
                () => false,
                () => new ChannelDTO(),
            );

            const suggestions = await suggestMentions('@', replyAction);

            expect(suggestions).toHaveLength(users.length);
            expect(suggestions.map((suggestion) => suggestion.label)).not.toContain('@all');
        });

        it('should fall back to the current conversation if the posting has no conversation', async () => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(new GroupChatDTO());
            const replyAction = new UserMentionAction(
                courseManagementService,
                communicationService,
                () => false,
                () => undefined,
            );

            const suggestions = await suggestMentions('@', replyAction);

            expect(suggestions[0].label).toBe('@all');
        });

        it('should not suggest @all while editing a posting of a group chat, even if the posting provides its conversation', async () => {
            const editingAction = new UserMentionAction(
                courseManagementService,
                communicationService,
                () => true,
                () => new GroupChatDTO(),
            );

            const suggestions = await suggestMentions('@', editingAction);

            expect(suggestions.map((suggestion) => suggestion.label)).not.toContain('@all');
        });

        it.each([
            { description: 'a channel', conversation: new ChannelDTO() },
            { description: 'a one-to-one chat', conversation: new OneToOneChatDTO() },
            { description: 'no conversation', conversation: undefined },
        ])('should not suggest @all in $description', async ({ conversation }) => {
            vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(conversation);

            const suggestions = await suggestMentions('@');

            expect(suggestions).toHaveLength(users.length);
            expect(suggestions.map((suggestion) => suggestion.label)).not.toContain('@all');
        });
    });

    describe('ExerciseReferenceAction (edge cases)', () => {
        it('should initialize with empty values if no exercise titles are available', async () => {
            vi.spyOn(exerciseService, 'getTitlesForCourse').mockReturnValue(of([]));
            const actionWithoutExercises = new ExerciseReferenceAction(communicationService, exerciseService);
            await firstValueFrom(exerciseService.getTitlesForCourse(1));
            fixture.detectChanges();
            comp.registerAction(actionWithoutExercises);
            expect(actionWithoutExercises.getValues()).toEqual([]);
        });

        it('should insert / for faq references', () => {
            fixture.detectChanges();
            comp.registerAction(faqReferenceAction);
            faqReferenceAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('/faq');
        });
    });

    describe('FaqReferenceAction', () => {
        it('should initialize with empty values if faqs are not available', () => {
            vi.spyOn(communicationService, 'getFaqs').mockReturnValue(of([]));

            fixture.detectChanges();
            comp.registerAction(faqReferenceAction);
            expect(faqReferenceAction.getValues()).toEqual([]);
        });

        it('should update values when faqs are loaded', () => {
            fixture.detectChanges();
            comp.registerAction(faqReferenceAction);

            const faq: Faq = { id: 99, questionTitle: 'Loaded FAQ', questionAnswer: 'Answer' };
            // Simulate REST-loaded FAQs arriving via the observable
            communicationService.setFaqs([faq]);

            expect(faqReferenceAction.getValues()).toEqual([{ id: faq.id!.toString(), value: faq.questionTitle!, type: 'faq' }]);
        });
    });

    describe('LectureAttachmentReferenceAction', () => {
        let lectures: Lecture[];
        let lectureAttachmentReferenceAction: LectureAttachmentReferenceAction;

        beforeEach(() => {
            lectures = communicationService.getCourse().lectures!;
            vi.spyOn(lectureService, 'findAllByCourseIdWithSlides').mockReturnValue(of(new HttpResponse({ body: lectures, status: 200 })));
            lectureAttachmentReferenceAction = new LectureAttachmentReferenceAction(communicationService, lectureService, fileService);
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should correctly initialize lecturesWithDetails', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);

            const lecturesWithDetails = lectures.map((lecture) => ({
                id: lecture.id!,
                title: lecture.title!,
                attachmentVideoUnits: lecture.lectureUnits?.filter((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO),
            }));

            expect(lectureAttachmentReferenceAction.lecturesWithDetails).toEqual(lecturesWithDetails);
        });

        it('should error on unsupported reference type', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({ reference: ReferenceType.PROGRAMMING, lecture: lectureAttachmentReferenceAction.lecturesWithDetails[0] });
            expect(executeAction).toThrow(Error);
        });

        it('should reference a lecture', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            lectureAttachmentReferenceAction.executeInCurrentEditor({ reference: ReferenceType.LECTURE, lecture });
            expect(comp.getText()).toBe(`[lecture]${lecture.title}(${communicationService.getLinkForLecture(lecture.id.toString())})[/lecture]`);
        });

        it('should reference a lecture without brackets', () => {
            fixture.detectChanges();

            const lectureNameWithBrackets = 'Test (Lecture) With [Brackets] And (More) [Bracket(s)]';
            const lectureNameWithoutBrackets = 'Test Lecture With Brackets And More Brackets';

            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const previousTitle = lecture.title;
            lecture.title = lectureNameWithBrackets;
            lectureAttachmentReferenceAction.executeInCurrentEditor({ reference: ReferenceType.LECTURE, lecture });
            lecture.title = previousTitle;
            expect(comp.getText()).toBe(`[lecture]${lectureNameWithoutBrackets}(${communicationService.getLinkForLecture(lecture.id.toString())})[/lecture]`);
        });

        it('should reference an attachment video unit without brackets', () => {
            fixture.detectChanges();

            const attachmentVideoUnitNameWithBrackets = 'Test (AttachmentVideoUnit) With [Brackets] And (More) [Bracket(s)]';
            const attachmentVideoUnitNameWithoutBrackets = 'Test AttachmentVideoUnit With Brackets And More Brackets';

            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];

            attachmentVideoUnit.attachment = {
                link: 'attachments/lectures/1/Communication-Attachment.pdf',
                studentVersion: 'attachments/attachment-video-units/1/student/Communication-Attachment.pdf',
                name: 'Communication-Attachment.pdf',
            } as Attachment;

            const previousName = attachmentVideoUnit.name;
            attachmentVideoUnit.name = attachmentVideoUnitNameWithBrackets;

            const attachmentVideoUnitFileName = 'attachment-video-units/1/student/Communication-Attachment.pdf';

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentVideoUnit: attachmentVideoUnit,
            });

            attachmentVideoUnit.name = previousName;
            expect(comp.getText()).toBe(`[lecture-unit]${attachmentVideoUnitNameWithoutBrackets}(${attachmentVideoUnitFileName})[/lecture-unit]`);
        });

        it('should reference an attachment video unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];

            attachmentVideoUnit.attachment = {
                link: '/api/files/attachments/Communication-Attachment.pdf',
                studentVersion: 'attachments/Communication-Attachment.pdf',
                name: 'Communication-Attachment.pdf',
                version: 2,
            } as Attachment;

            const attachmentVideoUnitFileName = 'Communication-Attachment.pdf?version=2';

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentVideoUnit: attachmentVideoUnit,
            });
            expect(comp.getText()).toBe(`[lecture-unit]${attachmentVideoUnit.name}(${attachmentVideoUnitFileName})[/lecture-unit]`);
        });

        it('should create a versioned fallback student link for an attachment video unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];

            attachmentVideoUnit.attachment = {
                link: 'attachments/attachment-video-units/123/Communication-Attachment.pdf',
                name: 'Communication-Attachment.pdf',
                version: 3,
            } as Attachment;
            vi.spyOn(fileService, 'createStudentLink').mockReturnValue('attachments/attachment-video-units/123/student/Communication-Attachment.pdf');

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentVideoUnit,
            });

            expect(comp.getText()).toBe(`[lecture-unit]${attachmentVideoUnit.name}(attachment-video-units/123/student/Communication-Attachment.pdf?version=3)[/lecture-unit]`);
        });

        it('should error when trying to reference a nonexistent attachment video unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({
                    reference: ReferenceType.ATTACHMENT_UNITS,
                    lecture,
                    attachmentVideoUnit: undefined,
                });
            expect(executeAction).toThrow(Error);
        });

        it('should reference a slide', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];
            const slide = attachmentVideoUnit.slides![0];

            // Ensure slide has a valid slideImagePath
            slide.slideImagePath = 'slide1.png';

            const slideIndex = 1;
            const slideId = 1;
            slide.id = slideId;

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.SLIDE,
                lecture,
                attachmentVideoUnit: attachmentVideoUnit,
                slide,
                slideIndex,
            });

            // Update the expectation to match the current implementation
            expect(comp.getText()).toBe(`[slide]${attachmentVideoUnit.name} Slide ${slideIndex}(#${slideId})[/slide]`);
        });

        it('should error when incorrectly referencing a slide', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({
                    reference: ReferenceType.SLIDE,
                    lecture,
                    attachmentVideoUnit: attachmentVideoUnit,
                    slide: undefined,
                });
            expect(executeAction).toThrow(Error);
        });
    });
});
