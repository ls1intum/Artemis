import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { MetisService } from 'app/communication/service/metis.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { ChannelReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/channel-reference.action';
import { UserMentionAction } from 'app/shared/monaco-editor/model/actions/communication/user-mention.action';
import { ExerciseReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/exercise-reference.action';
import { metisExamChannelDTO, metisExerciseChannelDTO, metisGeneralChannelDTO, metisTutor, metisUser1, metisUser2 } from 'test/helpers/sample/metis-sample-data';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import * as monaco from 'monaco-editor';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ReferenceType } from 'app/communication/metis.util';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import dayjs from 'dayjs/esm';
import { FaqReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/faq-reference.action';
import { Faq } from 'app/communication/shared/entities/faq.model';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { FileService } from 'app/shared/service/file.service';
import { ChannelIdAndNameDTO } from 'app/communication/shared/entities/conversation/channel.model';

describe('MonacoEditorCommunicationActionIntegration', () => {
    let comp: MonacoEditorComponent;
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let metisService: MetisService;
    let fileService: FileService;
    let courseManagementService: CourseManagementService;
    let channelService: ChannelService;
    let lectureService: LectureService;
    let provider: monaco.languages.CompletionItemProvider;

    // Actions
    let channelReferenceAction: ChannelReferenceAction;
    let userMentionAction: UserMentionAction;
    let exerciseReferenceAction: ExerciseReferenceAction;
    let faqReferenceAction: FaqReferenceAction;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LectureService),
                MockProvider(CourseManagementService),
                MockProvider(ChannelService),
            ],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(MonacoEditorComponent);
        comp = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        fileService = TestBed.inject(FileService);
        courseManagementService = TestBed.inject(CourseManagementService);
        lectureService = TestBed.inject(LectureService);
        channelService = TestBed.inject(ChannelService);
        channelReferenceAction = new ChannelReferenceAction(metisService, channelService);
        userMentionAction = new UserMentionAction(courseManagementService, metisService);
        exerciseReferenceAction = new ExerciseReferenceAction(metisService);
        faqReferenceAction = new FaqReferenceAction(metisService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const registerActionWithCompletionProvider = (action: TextEditorAction, triggerCharacter?: string) => {
        const registerCompletionProviderStub = jest.spyOn(monaco.languages, 'registerCompletionItemProvider').mockImplementation();
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
            channels = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            users = [metisUser1, metisUser2, metisTutor];
            jest.spyOn(courseManagementService, 'searchMembersForUserMentions').mockReturnValue(of(new HttpResponse({ body: users, status: 200 })));
            exercises = metisService.getCourse().exercises!;
            faqs = await firstValueFrom(metisService.getFaqs());

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
            jest.restoreAllMocks();
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
                    `[${exercises[index].type}]${exercises[index].title}(${metisService.getLinkForExercise(exercises[index].id!.toString())})[/${exercises[index].type}]`,
                );
                expect(suggestion.detail).toBe(exercises[index].type);
            });
        };

        const checkFaqSuggestions = (suggestions: monaco.languages.CompletionItem[], faqs: Faq[]) => {
            expect(suggestions).toHaveLength(faqs.length);
            suggestions.forEach((suggestion, index) => {
                expect(suggestion.label).toBe(`/faq ${faqs[index].questionTitle}`);
                expect(suggestion.insertText).toBe(`[faq]${faqs[index].questionTitle}(${metisService.getLinkForFaq()}?faqId=${faqs[index].id})[/faq]`);
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
            const channels: ChannelIdAndNameDTO[] = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            const getChannelsSpy = jest.spyOn(channelService, 'getPublicChannelsOfCourse');
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.fetchChannels()).toBe(channels);
            expect(getChannelsSpy).not.toHaveBeenCalled();
        });

        it('should load and cache channels if none are cached', async () => {
            const channels: ChannelIdAndNameDTO[] = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            const getChannelsStub = jest.spyOn(channelService, 'getPublicChannelsOfCourse').mockReturnValue(of(new HttpResponse({ body: channels, status: 200 })));
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.fetchChannels()).toBe(channels);
            expect(getChannelsStub).toHaveBeenCalledExactlyOnceWith(metisService.getCourse().id!);
            expect(channelReferenceAction.cachedChannels).toBe(channels);
        });

        it('should insert # for channel references', () => {
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            channelReferenceAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('#');
        });
    });

    describe('ExerciseReferenceAction (edge cases)', () => {
        it('should initialize with empty values if exercises are not available', () => {
            jest.spyOn(metisService, 'getCourse').mockReturnValue({ exercises: undefined } as any);
            fixture.detectChanges();
            comp.registerAction(exerciseReferenceAction);
            expect(exerciseReferenceAction.getValues()).toEqual([]);
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
            jest.spyOn(metisService, 'getFaqs').mockReturnValue(of([]));

            fixture.detectChanges();
            comp.registerAction(faqReferenceAction);
            expect(faqReferenceAction.getValues()).toEqual([]);
        });

        it('should update values when faqs are loaded', () => {
            fixture.detectChanges();
            comp.registerAction(faqReferenceAction);

            const faq: Faq = { id: 99, questionTitle: 'Loaded FAQ', questionAnswer: 'Answer' };
            // Simulate REST-loaded FAQs arriving via the observable
            metisService.setFaqs([faq]);

            expect(faqReferenceAction.getValues()).toEqual([{ id: faq.id!.toString(), value: faq.questionTitle!, type: 'faq' }]);
        });
    });

    describe('LectureAttachmentReferenceAction', () => {
        let lectures: Lecture[];
        let lectureAttachmentReferenceAction: LectureAttachmentReferenceAction;

        beforeEach(() => {
            lectures = metisService.getCourse().lectures!;
            jest.spyOn(lectureService, 'findAllByCourseIdWithSlides').mockReturnValue(of(new HttpResponse({ body: lectures, status: 200 })));
            lectureAttachmentReferenceAction = new LectureAttachmentReferenceAction(metisService, lectureService, fileService);
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should correctly initialize lecturesWithDetails', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);

            const lecturesWithDetails = lectures.map((lecture) => ({
                id: lecture.id!,
                title: lecture.title!,
                attachmentVideoUnits: lecture.lectureUnits?.filter((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO),
                attachments: lecture.attachments?.map((attachment) =>
                    Object.assign({}, attachment, {
                        link: attachment.link && attachment.name ? fileService.createAttachmentFileUrl(attachment.link, attachment.name, false) : attachment.link,
                        linkUrl:
                            attachment.link && attachment.name ? 'api/core/files/' + fileService.createAttachmentFileUrl(attachment.link, attachment.name, true) : attachment.link,
                    }),
                ),
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
            expect(comp.getText()).toBe(`[lecture]${lecture.title}(${metisService.getLinkForLecture(lecture.id.toString())})[/lecture]`);
        });

        it('should reference an attachment without brackets', () => {
            fixture.detectChanges();

            const attachmentNameWithBrackets = 'Test (File) With [Brackets] And (More) [Bracket(s)]';
            const attachmentNameWithoutBrackets = 'Test File With Brackets And More Brackets';

            const newAttachment = {
                id: 53,
                name: attachmentNameWithBrackets,
                link: '/api/core/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
                version: 1,
                uploadDate: dayjs('2019-05-07T08:49:59+02:00'),
                attachmentType: 'FILE',
            } as Attachment;

            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const shortLink = newAttachment.link?.split('attachments/')[1];
            lectureAttachmentReferenceAction.executeInCurrentEditor({ reference: ReferenceType.ATTACHMENT, lecture: lecture, attachment: newAttachment });
            expect(comp.getText()).toBe(`[attachment]${attachmentNameWithoutBrackets}(${shortLink})[/attachment]`);
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
            expect(comp.getText()).toBe(`[lecture]${lectureNameWithoutBrackets}(${metisService.getLinkForLecture(lecture.id.toString())})[/lecture]`);
        });

        it('should reference an attachment video unit without brackets', () => {
            fixture.detectChanges();

            const attachmentVideoUnitNameWithBrackets = 'Test (AttachmentVideoUnit) With [Brackets] And (More) [Bracket(s)]';
            const attachmentVideoUnitNameWithoutBrackets = 'Test AttachmentVideoUnit With Brackets And More Brackets';

            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];

            attachmentVideoUnit.attachment = {
                link: '/api/files/attachments/lecture/1/Metis-Attachment.pdf',
                studentVersion: 'attachments/lecture/1/Metis-Attachment.pdf',
                name: 'Metis-Attachment.pdf',
            } as Attachment;

            const previousName = attachmentVideoUnit.name;
            attachmentVideoUnit.name = attachmentVideoUnitNameWithBrackets;

            const attachmentVideoUnitFileName = 'lecture/1/Metis-Attachment.pdf';

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentVideoUnit: attachmentVideoUnit,
            });

            attachmentVideoUnit.name = previousName;
            expect(comp.getText()).toBe(`[lecture-unit]${attachmentVideoUnitNameWithoutBrackets}(${attachmentVideoUnitFileName})[/lecture-unit]`);
        });

        it('should reference an attachment', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const attachment = lecture.attachments![0];
            const attachmentFileName = 'Metis-Attachment.pdf';
            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT,
                lecture,
                attachment,
            });
            expect(comp.getText()).toBe(`[attachment]${attachment.name}(${attachmentFileName})[/attachment]`);
        });

        it('should error when trying to reference a nonexistent attachment', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({
                    reference: ReferenceType.ATTACHMENT,
                    lecture,
                    attachment: undefined,
                });
            expect(executeAction).toThrow(Error);
        });

        it('should reference an attachment video unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentVideoUnit = lecture.attachmentVideoUnits![0];

            attachmentVideoUnit.attachment = {
                link: '/api/files/attachments/Metis-Attachment.pdf',
                studentVersion: 'attachments/Metis-Attachment.pdf',
                name: 'Metis-Attachment.pdf',
            } as Attachment;

            const attachmentVideoUnitFileName = 'Metis-Attachment.pdf';

            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentVideoUnit: attachmentVideoUnit,
            });
            expect(comp.getText()).toBe(`[lecture-unit]${attachmentVideoUnit.name}(${attachmentVideoUnitFileName})[/lecture-unit]`);
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
            slide.slideImagePath = 'attachments/attachment-unit/123/slide/slide1.png';

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
