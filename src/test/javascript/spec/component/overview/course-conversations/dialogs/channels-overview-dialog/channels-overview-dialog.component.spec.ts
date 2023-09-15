import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ChannelAction, ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { initializeDialog } from '../dialog-test-helpers';
import { Course } from 'app/entities/course.model';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { EMPTY, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { generateExampleChannelDTO } from '../../helpers/conversationExampleModels';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapseMocksModule } from '../../../../../helpers/mocks/directive/ngbCollapseMocks.module';

@Component({
    selector: 'jhi-channel-item',
    template: '',
})
class ChannelItemStubComponent {
    @Output()
    channelAction = new EventEmitter<ChannelAction>();
    @Input()
    channel: ChannelDTO;
}

const examples: ChannelDTO[] = [
    generateExampleChannelDTO({}),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE }),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE }),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM }),
];

examples.forEach((exampleChannel) => {
    describe('ChannelsOverviewDialogComponent for ' + exampleChannel.subType + ' channels', () => {
        let component: ChannelsOverviewDialogComponent;
        let fixture: ComponentFixture<ChannelsOverviewDialogComponent>;
        const course = { id: 1, isAtLeastInstructor: true } as Course;
        const allowChannelCreation = exampleChannel.subType === ChannelSubType.GENERAL;
        const createChannelFn = allowChannelCreation ? jest.fn() : undefined;
        const canCreateChannel = jest.fn();
        let channelItems: ChannelItemStubComponent[];

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
                imports: [NgbCollapseMocksModule],
                declarations: [
                    ChannelsOverviewDialogComponent,
                    LoadingIndicatorContainerStubComponent,
                    ChannelItemStubComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(FaIconComponent),
                ],
                providers: [MockProvider(ChannelService), MockProvider(ConversationService), MockProvider(AlertService), MockProvider(NgbModal), MockProvider(NgbActiveModal)],
            }).compileComponents();
        }));

        afterEach(() => {
            jest.restoreAllMocks();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(ChannelsOverviewDialogComponent);
            component = fixture.componentInstance;
            channelOne = generateExampleChannelDTO({ id: 1, name: 'one', subType: exampleChannel.subType });
            channelTwo = generateExampleChannelDTO({ id: 2, name: 'two', subType: exampleChannel.subType });
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

            channelItems = fixture.debugElement.queryAll(By.directive(ChannelItemStubComponent)).map((debugElement) => debugElement.componentInstance);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(getChannelsOfCourseSpy).toHaveBeenCalledWith(course.id);
            expect(getChannelsOfCourseSpy).toHaveBeenCalledOnce();
            expect(component.noOfChannels).toBe(3);
            expect(component.otherChannels).toHaveLength(3);
            expect(fixture.nativeElement.querySelectorAll('jhi-channel-item')).toHaveLength(6);
            expect(channelItems).toHaveLength(6);
            expect(channelItems[0].channel).toEqual(channelOne);
            expect(channelItems[1].channel).toEqual(channelTwo);
        });

        it('should call register user when channel action is register', fakeAsync(() => {
            const channelAction = { channel: channelOne, action: 'register' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.detectChanges();
            tick(501);
            expect(registerUsersToChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(registerUsersToChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBeTrue();
        }));

        it('should call deregister user when channel action is deregister', fakeAsync(() => {
            const channelAction = { channel: channelOne, action: 'deregister' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.detectChanges();
            tick(501);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBeTrue();
        }));

        if (allowChannelCreation) {
            it('should close modal when channel action view is performed', fakeAsync(() => {
                const channelAction = { channel: channelOne, action: 'view' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.detectChanges();
                tick(501);
                expect(closeSpy).toHaveBeenCalledWith([channelOne, false]);
                expect(closeSpy).toHaveBeenCalledOnce();
            }));
        }

        if (createChannelFnSpy) {
            it('should call create channel function when channel action create is performed', fakeAsync(() => {
                const channelAction = { channel: new ChannelDTO(), action: 'create' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.detectChanges();
                tick(501);
                expect(createChannelFnSpy).toHaveBeenCalledOnce();
                expect(component.channelModificationPerformed).toBeTrue();
            }));
        }

        it('should not show create channel button if user is missing the permission', fakeAsync(() => {
            canCreateChannel.mockReturnValue(false);
            fixture.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('#createChannel')).toBeFalsy();
        }));

        if (allowChannelCreation) {
            it('should open create channel dialog when button is pressed', fakeAsync(() => {
                const modalService = TestBed.inject(NgbModal);
                const mockModalRef = {
                    componentInstance: { course: undefined, initialize: () => {} },
                    result: Promise.resolve(new ChannelDTO()),
                };
                const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
                fixture.detectChanges();

                const createChannelButton = fixture.debugElement.nativeElement.querySelector('#createChannel');
                createChannelButton.click();
                tick(501);
                fixture.whenStable().then(() => {
                    expect(openDialogSpy).toHaveBeenCalledOnce();
                    expect(openDialogSpy).toHaveBeenCalledWith(ChannelsCreateDialogComponent, defaultSecondLayerDialogOptions);
                    expect(mockModalRef.componentInstance.course).toEqual(course);
                });
            }));
        } else {
            it('should hide create channel button if creation is not allowed', () => {
                const createChannelButton = fixture.debugElement.nativeElement.querySelector('#createChannel');
                expect(createChannelButton).toBeNull();
            });
        }

        it('should toggle other channels on click', () => {
            expect(component.otherChannelsAreCollapsed).toBeTrue();

            const otherChannels = fixture.debugElement.nativeElement.querySelector('.other-channels');
            otherChannels.click();

            expect(component.otherChannelsAreCollapsed).toBeFalse();
        });
    });
});
