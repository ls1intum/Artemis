import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { EMPTY, Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { generateExampleChannelDTO } from 'test/helpers/sample/conversationExampleModels';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { AlertService } from 'app/shared/service/alert.service';
import { DialogService } from 'primeng/dynamicdialog';
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
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

const examples: ChannelDTO[] = [
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM } as ChannelDTO),
];

examples.forEach((exampleChannel) => {
    describe('ChannelsOverviewDialogComponent for ' + exampleChannel.subType + ' channels', () => {
        setupTestBed({ zoneless: true });

        let component: ChannelsOverviewDialogComponent;
        let fixture: ComponentFixture<ChannelsOverviewDialogComponent>;
        const course = { id: 1, isAtLeastInstructor: true } as Course;
        const allowChannelCreation = exampleChannel.subType === ChannelSubType.GENERAL;
        const createChannelFn = allowChannelCreation ? vi.fn() : undefined;
        const canCreateChannel = vi.fn();
        let channelItems: ChannelItemComponent[];

        let channelOne: ChannelDTO;
        let channelTwo: ChannelDTO;
        let channelService: ChannelService;
        let getChannelsOfCourseSpy: ReturnType<typeof vi.spyOn>;
        let registerUsersToChannelSpy: ReturnType<typeof vi.spyOn>;
        let deregisterUsersFromChannelSpy: ReturnType<typeof vi.spyOn>;
        let closeSpy: ReturnType<typeof vi.spyOn>;
        let createChannelFnSpy: ReturnType<typeof vi.spyOn> | undefined;

        beforeEach(async () => {
            TestBed.configureTestingModule({
                imports: [ChannelsOverviewDialogComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
                declarations: [LoadingIndicatorContainerStubComponent],
                providers: [
                    MockProvider(ChannelService),
                    MockProvider(ConversationService),
                    MockProvider(AlertService),
                    MockProvider(DialogService),
                    { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                    { provide: DynamicDialogConfig, useValue: { data: {} } },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            });
        });

        afterEach(() => {
            vi.useRealTimers();
            vi.restoreAllMocks();
        });

        beforeEach(() => {
            vi.useFakeTimers();
            fixture = TestBed.createComponent(ChannelsOverviewDialogComponent);
            component = fixture.componentInstance;
            channelOne = generateExampleChannelDTO({ id: 1, name: 'one', subType: exampleChannel.subType } as ChannelDTO);
            channelTwo = generateExampleChannelDTO({ id: 2, name: 'two', subType: exampleChannel.subType } as ChannelDTO);
            channelService = TestBed.inject(ChannelService);
            getChannelsOfCourseSpy = vi.spyOn(channelService, 'getChannelsOfCourse').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [channelOne, channelTwo, ...examples],
                        status: 200,
                    }),
                ),
            );

            registerUsersToChannelSpy = vi.spyOn(channelService, 'registerUsersToChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
            deregisterUsersFromChannelSpy = vi.spyOn(channelService, 'deregisterUsersFromChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
            closeSpy = vi.spyOn(component.dialogRef!, 'close');
            createChannelFnSpy = createChannelFn?.mockReturnValue(EMPTY);

            fixture.detectChanges();
            canCreateChannel.mockReturnValue(true);
            component.canCreateChannel = canCreateChannel;
            initializeDialog(component, fixture, { course, channelSubType: exampleChannel.subType, createChannelFn });

            channelItems = fixture.debugElement.queryAll(By.css('jhi-channel-item')).map((debugElement) => debugElement.componentInstance);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(getChannelsOfCourseSpy).toHaveBeenCalledWith(course.id);
            expect(getChannelsOfCourseSpy).toHaveBeenCalledOnce();
            expect(component.noOfChannels).toBe(6);
            expect(fixture.nativeElement.querySelectorAll('jhi-channel-item')).toHaveLength(6);
            expect(channelItems).toHaveLength(6);
            expect(channelItems[0].channel()).toEqual(channelOne);
            expect(channelItems[1].channel()).toEqual(channelTwo);
        });

        it('should call register user when channel action is register', () => {
            const channelAction = { channel: channelOne, action: 'register' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(501);
            expect(registerUsersToChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(registerUsersToChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBe(true);
        });

        it('should call deregister user when channel action is deregister', () => {
            const channelAction = { channel: channelOne, action: 'deregister' } as ChannelAction;
            channelItems[0].channelAction.emit(channelAction);
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(501);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledWith(course.id, channelOne.id);
            expect(deregisterUsersFromChannelSpy).toHaveBeenCalledOnce();
            expect(component.channelModificationPerformed).toBe(true);
        });

        if (allowChannelCreation) {
            it('should close modal when channel action view is performed', () => {
                const channelAction = { channel: channelOne, action: 'view' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.changeDetectorRef.detectChanges();
                vi.advanceTimersByTime(501);
                expect(closeSpy).toHaveBeenCalledWith([channelOne, false]);
                expect(closeSpy).toHaveBeenCalledOnce();
            });
        }

        if (createChannelFnSpy) {
            it('should call create channel function when channel action create is performed', () => {
                const channelAction = { channel: new ChannelDTO(), action: 'create' } as ChannelAction;
                channelItems[0].channelAction.emit(channelAction);
                fixture.changeDetectorRef.detectChanges();
                vi.advanceTimersByTime(501);
                expect(createChannelFnSpy).toHaveBeenCalledOnce();
                expect(component.channelModificationPerformed).toBe(true);
            });
        }
    });
});
