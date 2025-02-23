import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FormBuilder } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { directMessageUser1, metisPostToCreateUser1 } from '../../../../helpers/sample/metis-sample-data';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { provideHttpClient } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockMetisConversationService } from '../../../../helpers/mocks/service/mock-metis-conversation.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { mockSettings } from '../../../iris/settings/mock-settings';
import { ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { ConversationDTO, ConversationType } from 'app/entities/metis/conversation/conversation.model';

describe('MessageInlineInputComponent', () => {
    let component: MessageInlineInputComponent;
    let fixture: ComponentFixture<MessageInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;
    let irisSettingsService: IrisSettingsService;
    const irisSettings = mockSettings();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [MessageInlineInputComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                MockProvider(IrisSettingsService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MessageInlineInputComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceCreateStub = jest.spyOn(metisService, 'createPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updatePost');
                irisSettingsService = TestBed.inject(IrisSettingsService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should invoke metis service with created post', fakeAsync(() => {
        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message creation', fakeAsync(() => {
        metisServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');

        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).not.toHaveBeenCalled();
    }));

    it('should invoke metis service with edited post', fakeAsync(() => {
        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: editedContent,
            title: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message updating', fakeAsync(() => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).not.toHaveBeenCalled();
    }));

    describe('iris redirect button', () => {
        it('should be enabled for exercise chats if Iris is activated for the exercise', fakeAsync(() => {
            const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));

            component.posting = directMessageUser1;

            const mockChannelDTO = {
                type: ConversationType.CHANNEL,
                subType: ChannelSubType.EXERCISE,
                subTypeReferenceId: 42,
            } as ConversationDTO;

            jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

            component.ngOnChanges();
            tick();

            expect(component.irisEnabled).toBeTrue();
            expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
        }));

        it('should be enabled for lecture chats if Iris is activated for the lecture', fakeAsync(() => {
            const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedExerciseSettings').mockReturnValue(of(irisSettings));

            component.posting = directMessageUser1;

            const mockChannelDTO = {
                type: ConversationType.CHANNEL,
                subType: ChannelSubType.LECTURE,
                subTypeReferenceId: 42,
            } as ConversationDTO;

            jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

            component.ngOnChanges();
            tick();

            expect(component.irisEnabled).toBeTrue();
            expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
        }));

        it('should be disabled for exercise chats if Iris is disabled for the exercise', fakeAsync(() => {
            const disabledIrisSettings = mockSettings();
            disabledIrisSettings.irisLectureChatSettings!.enabled = false;
            const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(disabledIrisSettings));

            component.posting = directMessageUser1;

            const mockChannelDTO = {
                type: ConversationType.CHANNEL,
                subType: ChannelSubType.EXERCISE,
                subTypeReferenceId: 42,
            } as ConversationDTO;

            jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

            component.ngOnChanges();
            tick();

            expect(component.irisEnabled).toBeFalse();
            expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
        }));

        it('should add the message as payload when pressed', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));

            component.posting = directMessageUser1;

            component.resetFormGroup();
            component.formGroup.setValue({ content: 'Test' });

            const mockChannelDTO = {
                type: ConversationType.CHANNEL,
                subType: ChannelSubType.EXERCISE,
                subTypeReferenceId: 42,
            } as ConversationDTO;

            jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

            const routerNavigateSpy = jest.spyOn(component.router, 'navigate').mockImplementation(() => Promise.resolve(true));

            component.ngOnChanges();
            tick();
            component.redirectToIris();
            let correctLink = metisService.getLinkForExercise('42');

            expect(routerNavigateSpy).toHaveBeenCalledWith([correctLink], { queryParams: { irisQuestion: directMessageUser1.content } });
        }));
    });
});
