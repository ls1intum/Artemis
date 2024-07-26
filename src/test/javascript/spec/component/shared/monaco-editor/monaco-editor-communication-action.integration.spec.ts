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

describe('MonacoEditorCommunicationActionIntegration', () => {
    let comp: MonacoEditorComponent;
    let fixture: ComponentFixture<MonacoEditorComponent>;
    // let debugElement: DebugElement;
    let metisService: MetisService;
    let courseManagementService: CourseManagementService;
    let channelService: ChannelService;
    // let lectureService: LectureService;
    let provider: monaco.languages.CompletionItemProvider;

    // Actions
    let channelReferenceAction: MonacoChannelReferenceAction;
    let userMentionAction: MonacoUserMentionAction;
    // let lectureAttachmentReferenceAction: MonacoLectureAttachmentReferenceAction;
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
                // lectureService = TestBed.inject(LectureService);
                channelService = TestBed.inject(ChannelService);
                channelReferenceAction = new MonacoChannelReferenceAction(metisService, channelService);
                userMentionAction = new MonacoUserMentionAction(courseManagementService, metisService);
                // lectureAttachmentReferenceAction = new MonacoLectureAttachmentReferenceAction(metisService, lectureService);
                exerciseReferenceAction = new MonacoExerciseReferenceAction(metisService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(comp).toBeTruthy();
    });

    const registerActionWithCompletionProvider = (action: MonacoEditorAction, triggerCharacter?: string) => {
        const registerCompletionProviderStub = jest.spyOn(monaco.languages, 'registerCompletionItemProvider').mockImplementation();
        comp.registerAction(action);
        expect(registerCompletionProviderStub).toHaveBeenCalledOnce();
        provider = registerCompletionProviderStub.mock.calls[0][1];
        expect(provider).toBeDefined();
        expect(provider.provideCompletionItems).toBeDefined();
        expect(provider.triggerCharacters).toEqual(triggerCharacter ? [triggerCharacter] : undefined);
    };

    // TODO: refactor to reduce redundancy
    describe('MonacoChannelReferenceAction', () => {
        it('should use cached channels if available', async () => {
            const channels: ChannelIdAndNameDTO[] = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            channelReferenceAction.cachedChannels = channels;
            const getChannelsSpy = jest.spyOn(channelService, 'getPublicChannelsOfCourse');
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.getChannels()).toBe(channels);
            expect(getChannelsSpy).not.toHaveBeenCalled();
        });

        it('should load and cache channels if none are cached', async () => {
            const channels: ChannelIdAndNameDTO[] = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];
            const getChannelsStub = jest.spyOn(channelService, 'getPublicChannelsOfCourse').mockReturnValue(of(new HttpResponse({ body: channels, status: 200 })));
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            expect(await channelReferenceAction.getChannels()).toBe(channels);
            expect(getChannelsStub).toHaveBeenCalledExactlyOnceWith(metisService.getCourse().id!);
            expect(channelReferenceAction.cachedChannels).toBe(channels);
        });

        it('should insert # for channel references', () => {
            fixture.detectChanges();
            comp.registerAction(channelReferenceAction);
            channelReferenceAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('#');
        });

        describe('Suggestions', () => {
            const channels = [metisGeneralChannelDTO, metisExamChannelDTO, metisExerciseChannelDTO];

            beforeEach(() => {
                fixture.detectChanges();
                channelReferenceAction.cachedChannels = channels;
                comp.changeModel('initial', '');
            });

            afterEach(() => {
                jest.restoreAllMocks();
            });

            it('should suggest no channels for the wrong model', async () => {
                registerActionWithCompletionProvider(channelReferenceAction, '#');
                comp.changeModel('other', '#ch');
                const suggestions = await provider.provideCompletionItems(comp.models[1], new monaco.Position(1, 4), {} as any, {} as any);
                expect(suggestions).toBeUndefined();
            });

            it('should suggest no channels if the user is not typing a channel reference', async () => {
                comp.setText('some text that is no channel reference');
                registerActionWithCompletionProvider(channelReferenceAction, '#');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, 4), {} as any, {} as any);
                expect(providerResult).toBeUndefined();
            });

            it.each([
                { text: '#ch', column: 4 },
                // Edge case: The cursor is immediately after the #
                { text: '#', column: 2 },
            ])('should suggest channels if the user is typing a channel reference (text $text)', async ({ text, column }) => {
                comp.setText(text);
                registerActionWithCompletionProvider(channelReferenceAction, '#');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, column), {} as any, {} as any);
                expect(providerResult).toBeDefined();
                expect(providerResult!.incomplete).toBeUndefined();
                expect(providerResult!.suggestions).toHaveLength(channels.length);
                providerResult!.suggestions.forEach((suggestion, index) => {
                    expect(suggestion.label).toBe(`#${channels[index].name}`);
                    expect(suggestion.insertText).toBe(`[channel]${channels[index].name}(${channels[index].id})[/channel]`);
                    expect(suggestion.detail).toBe(channelReferenceAction.label);
                });
            });
        });
    });

    describe('MonacoUserMentionAction', () => {
        beforeEach(() => {
            fixture.detectChanges();
            comp.changeModel('initial', '');
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should insert @ for user mentions', () => {
            fixture.detectChanges();
            comp.registerAction(userMentionAction);
            userMentionAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('@');
        });

        describe('Suggestions', () => {
            let users: User[];

            beforeEach(() => {
                fixture.detectChanges();
                comp.changeModel('initial', '');
                users = [metisUser1, metisUser2, metisTutor];
                jest.spyOn(courseManagementService, 'searchMembersForUserMentions').mockReturnValue(of(new HttpResponse({ body: users, status: 200 })));
            });

            afterEach(() => {
                jest.restoreAllMocks();
            });

            it('should suggest no users for the wrong model', async () => {
                registerActionWithCompletionProvider(userMentionAction, '@');
                comp.changeModel('other', '@us');
                const suggestions = await provider.provideCompletionItems(comp.models[1], new monaco.Position(1, 4), {} as any, {} as any);
                expect(suggestions).toBeUndefined();
            });

            it('should suggest no users if the user is not typing a user mention', async () => {
                comp.setText('some text that is no user mention');
                registerActionWithCompletionProvider(userMentionAction, '@');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, 4), {} as any, {} as any);
                expect(providerResult).toBeUndefined();
            });

            it.each([
                { text: '@us', column: 4 },
                // Edge case: The cursor is immediately after the @
                { text: '@', column: 2 },
            ])('should suggest users if the user is typing a user mention (text $text)', async ({ text, column }) => {
                comp.setText(text);
                registerActionWithCompletionProvider(userMentionAction, '@');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, column), {} as any, {} as any);
                expect(providerResult).toBeDefined();
                expect(providerResult!.incomplete).toBeTrue();
                expect(providerResult!.suggestions).toHaveLength(users.length);
                providerResult!.suggestions.forEach((suggestion, index) => {
                    expect(suggestion.label).toBe(`@${users[index].name}`);
                    expect(suggestion.insertText).toBe(`[user]${users[index].name}(${users[index].login})[/user]`);
                    expect(suggestion.detail).toBe(userMentionAction.label);
                });
            });
        });
    });

    describe('MonacoExerciseReferenceAction', () => {
        let exercises: Exercise[];

        beforeEach(() => {
            fixture.detectChanges();
            comp.changeModel('initial', '');
            exercises = metisService.getCourse().exercises!;
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should initialize with empty values if exercises are not available', () => {
            jest.spyOn(metisService, 'getCourse').mockReturnValue({ exercises: undefined } as any);
            fixture.detectChanges();
            comp.registerAction(exerciseReferenceAction);
            expect(exerciseReferenceAction.getValues()).toEqual([]);
        });

        it('should insert /exercise for exercise references', () => {
            fixture.detectChanges();
            comp.registerAction(exerciseReferenceAction);
            exerciseReferenceAction.executeInCurrentEditor();
            expect(comp.getText()).toBe('/exercise');
        });

        describe('Suggestions', () => {
            beforeEach(() => {
                fixture.detectChanges();
                comp.changeModel('initial', '');
            });

            afterEach(() => {
                jest.restoreAllMocks();
            });

            it('should suggest no exercises for the wrong model', async () => {
                registerActionWithCompletionProvider(exerciseReferenceAction, '/');
                comp.changeModel('other', '/ex');
                const suggestions = await provider.provideCompletionItems(comp.models[1], new monaco.Position(1, 4), {} as any, {} as any);
                expect(suggestions).toBeUndefined();
            });

            it('should suggest no exercises if the user is not typing an exercise reference', async () => {
                comp.setText('some text that is no exercise reference');
                registerActionWithCompletionProvider(exerciseReferenceAction, '/');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, 4), {} as any, {} as any);
                expect(providerResult).toBeUndefined();
            });

            it.each([
                { text: '/ex', column: 4 },
                // Edge case: The cursor is immediately after the /
                { text: '/', column: 2 },
            ])('should suggest exercises if the user is typing an exercise reference (text $text)', async ({ text, column }) => {
                comp.setText(text);
                registerActionWithCompletionProvider(exerciseReferenceAction, '/');
                const providerResult = await provider.provideCompletionItems(comp.models[0], new monaco.Position(1, column), {} as any, {} as any);
                expect(providerResult).toBeDefined();
                expect(providerResult!.incomplete).toBeUndefined();
                expect(providerResult!.suggestions).toHaveLength(exercises.length);
                providerResult!.suggestions.forEach((suggestion, index) => {
                    expect(suggestion.label).toBe(`/exercise ${exercises[index].title}`);
                    expect(suggestion.insertText).toBe(
                        `[${exercises[index].type}]${exercises[index].title}(${metisService.getLinkForExercise(exercises[index].id!.toString())})[/${exercises[index].type}]`,
                    );
                    expect(suggestion.detail).toBe(exercises[index].type);
                });
            });
        });
    });
});
