import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { MetisService } from 'app/shared/metis/metis.service';
import { LectureService } from 'app/lecture/lecture.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoChannelReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-channel-reference.action';
import { MonacoUserMentionAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-user-mention.action';
import { MonacoExerciseReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-exercise-reference.action';
import { metisExamChannelDTO, metisExerciseChannelDTO, metisGeneralChannelDTO, metisTutor, metisUser1, metisUser2 } from '../../../helpers/sample/metis-sample-data';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ChannelIdAndNameDTO } from 'app/entities/metis/conversation/channel.model';
import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MonacoLectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-lecture-attachment-reference.action';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ReferenceType } from 'app/shared/metis/metis.util';

describe('MonacoEditorCommunicationActionIntegration', () => {
    let comp: MonacoEditorComponent;
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let metisService: MetisService;
    let courseManagementService: CourseManagementService;
    let channelService: ChannelService;
    let lectureService: LectureService;
    let provider: monaco.languages.CompletionItemProvider;

    // Actions
    let channelReferenceAction: MonacoChannelReferenceAction;
    let userMentionAction: MonacoUserMentionAction;
    let exerciseReferenceAction: MonacoExerciseReferenceAction;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                MockProvider(LectureService),
                MockProvider(CourseManagementService),
                MockProvider(ChannelService),
            ],
            declarations: [MonacoEditorComponent],
        })
            .compileComponents()
            .then(() => {
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                fixture = TestBed.createComponent(MonacoEditorComponent);
                comp = fixture.componentInstance;
                // debugElement = fixture.debugElement;
                metisService = TestBed.inject(MetisService);
                courseManagementService = TestBed.inject(CourseManagementService);
                lectureService = TestBed.inject(LectureService);
                channelService = TestBed.inject(ChannelService);
                channelReferenceAction = new MonacoChannelReferenceAction(metisService, channelService);
                userMentionAction = new MonacoUserMentionAction(courseManagementService, metisService);
                exerciseReferenceAction = new MonacoExerciseReferenceAction(metisService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const registerActionWithCompletionProvider = (action: MonacoEditorAction, triggerCharacter?: string) => {
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
        { actionId: MonacoChannelReferenceAction.ID, defaultInsertText: '#', triggerCharacter: '#' },
        { actionId: MonacoUserMentionAction.ID, defaultInsertText: '@', triggerCharacter: '@' },
        { actionId: MonacoExerciseReferenceAction.ID, defaultInsertText: '/exercise', triggerCharacter: '/' },
    ])('Suggestions and default behavior for $actionId', ({ actionId, defaultInsertText, triggerCharacter }) => {
        let action: MonacoChannelReferenceAction | MonacoUserMentionAction | MonacoExerciseReferenceAction;
        let channels: ChannelIdAndNameDTO[];
        let users: User[];
        let exercises: Exercise[];

        beforeEach(() => {
            fixture.detectChanges();
            comp.changeModel('initial', '');
            channels = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            users = [metisUser1, metisUser2, metisTutor];
            jest.spyOn(courseManagementService, 'searchMembersForUserMentions').mockReturnValue(of(new HttpResponse({ body: users, status: 200 })));
            exercises = metisService.getCourse().exercises!;

            switch (actionId) {
                case MonacoChannelReferenceAction.ID:
                    action = channelReferenceAction;
                    break;
                case MonacoUserMentionAction.ID:
                    action = userMentionAction;
                    break;
                case MonacoExerciseReferenceAction.ID:
                    action = exerciseReferenceAction;
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

        it.each(['', 'ex'])('should suggest the correct values if the user is typing a reference (suffix "%s")', async (referenceSuffix: string) => {
            const reference = triggerCharacter + referenceSuffix;
            comp.setText(reference);
            const column = reference.length + 1;
            registerActionWithCompletionProvider(action, triggerCharacter);
            const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, column), {} as any, {} as any);
            expect(providerResult).toBeDefined();
            expect(providerResult!.incomplete).toBe(actionId === MonacoUserMentionAction.ID ? true : undefined);
            const suggestions = providerResult!.suggestions;
            switch (actionId) {
                case MonacoChannelReferenceAction.ID:
                    checkChannelSuggestions(suggestions, channels);
                    break;
                case MonacoUserMentionAction.ID:
                    checkUserSuggestions(suggestions, users);
                    break;
                case MonacoExerciseReferenceAction.ID:
                    checkExerciseSuggestions(suggestions, exercises);
                    break;
            }
        });
    });

    describe('MonacoChannelReferenceAction', () => {
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

    describe('MonacoExerciseReferenceAction (edge cases)', () => {
        it('should initialize with empty values if exercises are not available', () => {
            jest.spyOn(metisService, 'getCourse').mockReturnValue({ exercises: undefined } as any);
            fixture.detectChanges();
            comp.registerAction(exerciseReferenceAction);
            expect(exerciseReferenceAction.getValues()).toEqual([]);
        });
    });

    describe('MonacoLectureAttachmentReferenceAction', () => {
        let lectures: Lecture[];
        let lectureAttachmentReferenceAction: MonacoLectureAttachmentReferenceAction;

        beforeEach(() => {
            lectures = metisService.getCourse().lectures!;
            jest.spyOn(lectureService, 'findAllByCourseIdWithSlides').mockReturnValue(of(new HttpResponse({ body: lectures, status: 200 })));
            lectureAttachmentReferenceAction = new MonacoLectureAttachmentReferenceAction(metisService, lectureService);
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
                attachmentUnits: lecture.lectureUnits?.filter((unit) => unit.type === LectureUnitType.ATTACHMENT),
                attachments: lecture.attachments,
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

        it('should reference an attachment unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentUnit = lecture.attachmentUnits![0];
            const attachmentUnitFileName = 'Metis-Attachment.pdf';
            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.ATTACHMENT_UNITS,
                lecture,
                attachmentUnit,
            });
            expect(comp.getText()).toBe(`[lecture-unit]${attachmentUnit.name}(${attachmentUnitFileName})[/lecture-unit]`);
        });

        it('should error when trying to reference a nonexistent attachment unit', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[0];
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({
                    reference: ReferenceType.ATTACHMENT_UNITS,
                    lecture,
                    attachmentUnit: undefined,
                });
            expect(executeAction).toThrow(Error);
        });

        it('should reference a slide', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentUnit = lecture.attachmentUnits![0];
            const slide = attachmentUnit.slides![0];
            const slideLink = 'slides';
            lectureAttachmentReferenceAction.executeInCurrentEditor({
                reference: ReferenceType.SLIDE,
                lecture,
                attachmentUnit,
                slide,
            });
            expect(comp.getText()).toBe(`[slide]${attachmentUnit.name} Slide ${slide.slideNumber}(${slideLink})[/slide]`);
        });

        it('should error when incorrectly referencing a slide', () => {
            fixture.detectChanges();
            comp.registerAction(lectureAttachmentReferenceAction);
            const lecture = lectureAttachmentReferenceAction.lecturesWithDetails[2];
            const attachmentUnit = lecture.attachmentUnits![0];
            const executeAction = () =>
                lectureAttachmentReferenceAction.executeInCurrentEditor({
                    reference: ReferenceType.SLIDE,
                    lecture,
                    attachmentUnit,
                    slide: undefined,
                });
            expect(executeAction).toThrow(Error);
        });
    });
});
