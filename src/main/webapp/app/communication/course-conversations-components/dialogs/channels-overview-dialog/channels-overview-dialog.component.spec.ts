import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { EMPTY, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { generateExampleChannelDTO } from 'test/helpers/sample/conversationExampleModels';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import {
    ChannelAction,
    ChannelsOverviewDialogComponent,
} from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelItemComponent } from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channel-item/channel-item.component';

const examples: ChannelDTO[] = [
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM } as ChannelDTO),
];

examples.forEach((exampleChannel) => {
    describe('ChannelsOverviewDialogComponent for ' + exampleChannel.subType + ' channels', () => {
        let component: ChannelsOverviewDialogComponent;
        let fixture: ComponentFixture<ChannelsOverviewDialogComponent>;
        const course = { id: 1, isAtLeastInstructor: true } as Course;
        const allowChannelCreation = exampleChannel.subType === ChannelSubType.GENERAL;
        const createChannelFn = allowChannelCreation ? jest.fn() : undefined;
        const canCreateChannel = jest.fn();
        let channelItems: ChannelItemComponent[];

        let channelOne: ChannelDTO;
        let channelTwo: ChannelDTO;
        let channelService: ChannelService;
        let getChannelsOfCourseSpy: jest.SpyInstance;
        let registerUsersToChannelSpy: jest.SpyInstance;
        let deregisterUsersFromChannelSpy: jest.SpyInstance;
        let closeSpy: jest.SpyInstance;
        let createChannelFnSpy: jest.SpyInstance | undefined;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [ChannelsOverviewDialogComponent, LoadingIndicatorContainerStubComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
                providers: [
                    MockProvider(ChannelService),
                    MockProvider(ConversationService),
                    MockProvider(AlertService),
                    MockProvider(NgbModal),
                    MockProvider(NgbActiveModal),
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            }).compileComponents();
        }));

        afterEach(() => {
            jest.restoreAllMocks();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(ChannelsOverviewDialogComponent);
            component = fixture.componentInstance;
            channelOne = generateExampleChannelDTO({ id: 1, name: 'one', subType: exampleChannel.subType } as ChannelDTO);
            channelTwo = generateExampleChannelDTO({ id: 2, name: 'two', subType: exampleChannel.subType } as ChannelDTO);
            channelService = TestBed.inject(ChannelService);
            getChannelsOfCourseSpy = jest.spyOn(channelService, 'getChannelsOfCourse').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [channelOne, channelTwo, ...examples],
                        status: 200,
                    }),
                ),
            );

            registerUsersToChannelSpy = jest.spyOn(channelService, 'registerUsersToChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
            deregisterUsersFromChannelSpy = jest.spyOn(channelService, 'deregisterUsersFromChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
            closeSpy = jest.spyOn(component.activeModal, 'close');
            createChannelFnSpy = createChannelFn?.mockReturnValue(EMPTY);

            fixture.detectChanges();
            canCreateChannel.mockReturnValue(true);
            component.canCreateChannel = canCreateChannel;
            initializeDialog(component, fixture, { course, createChannelFn, channelSubType: exampleChannel.subType });

            channelItems = fixture.debugElement.queryAll(By.css('jhi-channel-item')).map((debugElement) => debugElement.componentInstance);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(getChannelsOfCourseSpy).toHaveBeenCalledWith(course.id);
            expect(getChannelsOfCourseSpy).toHaveBeenCalledOnce();
            expect(component.noOfChannels).toBe(6);
            expect(fixture.nativeElement.querySelectorAll('jhi-channel-item')).toHaveLength(6);
            expect(channelItems).toHaveLength(6);
            expect(channelItems[0].channel).toEqual(channelOne);
            expect(channelItems[1].channel).toEqual(channelTwo);
        });

        it('should call register user when channel action is register', fakeAsync(() => {
            const channelAction = { channel: channelOne, action: 'register' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.changeDetectorRef.detectChanges();
            tick(501);
            expect(registerUsersToChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(registerUsersToChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBeTrue();
        }));

        it('should call deregister user when channel action is deregister', fakeAsync(() => {
            const channelAction = { channel: channelOne, action: 'deregister' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.changeDetectorRef.detectChanges();
            tick(501);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBeTrue();
        }));

        if (allowChannelCreation) {
            it('should close modal when channel action view is performed', fakeAsync(() => {
                const channelAction = { channel: channelOne, action: 'view' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.changeDetectorRef.detectChanges();
                tick(501);
                expect(closeSpy).toHaveBeenCalledWith([channelOne, false]);
                expect(closeSpy).toHaveBeenCalledOnce();
            }));
        }

        if (createChannelFnSpy) {
            it('should call create channel function when channel action create is performed', fakeAsync(() => {
                const channelAction = { channel: new ChannelDTO(), action: 'create' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.changeDetectorRef.detectChanges();
                tick(501);
                expect(createChannelFnSpy).toHaveBeenCalledOnce();
                expect(component.channelModificationPerformed).toBeTrue();
            }));
        }
    });
});
