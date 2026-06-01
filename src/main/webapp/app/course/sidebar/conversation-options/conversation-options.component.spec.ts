import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationOptionsComponent } from 'app/course/sidebar/conversation-options/conversation-options.component';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { CourseExerciseDetailsComponent } from 'app/course/overview/exercise-details/course-exercise-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-metis-service.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const examples: (() => ConversationDTO)[] = [
    () => generateOneToOneChatDTO({}),
    () => generateExampleGroupChatDTO({}),
    () => generateExampleChannelDTO({} as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE, subTypeReferenceId: 1 } as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.LECTURE, subTypeReferenceId: 1 } as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.EXAM, subTypeReferenceId: 1 } as ChannelDTO),
];

examples.forEach((conversation) => {
    const testDescription = conversation();

    describe('ConversationOptionsComponent with ' + (testDescription instanceof ChannelDTO ? testDescription.subType + ' ' : '') + testDescription.type, () => {
        setupTestBed({ zoneless: true });
        let component: ConversationOptionsComponent;
        let fixture: ComponentFixture<ConversationOptionsComponent>;
        let conversationService: ConversationService;
        let updateIsFavoriteSpy: ReturnType<typeof vi.spyOn>;
        let updateIsHiddenSpy: ReturnType<typeof vi.spyOn>;
        let updateIsMutedSpy: ReturnType<typeof vi.spyOn>;
        const course = { id: 1 } as any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [
                    provideRouter([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                    MockProvider(ConversationService),
                    MockProvider(MetisConversationService),
                    MockProvider(AlertService),
                    MockProvider(DialogService),
                    { provide: MetisService, useClass: MockMetisService },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(ConversationOptionsComponent);
            conversationService = TestBed.inject(ConversationService);
            updateIsFavoriteSpy = vi.spyOn(conversationService, 'updateIsFavorite').mockReturnValue(of(new HttpResponse<void>()));
            updateIsHiddenSpy = vi.spyOn(conversationService, 'updateIsHidden').mockReturnValue(of(new HttpResponse<void>()));
            updateIsMutedSpy = vi.spyOn(conversationService, 'updateIsMuted').mockReturnValue(of(new HttpResponse<void>()));

            component = fixture.componentInstance;
            fixture.componentRef.setInput('conversation', conversation());
            component.course = course;
            fixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should remove conversation from favorites when hidden', async () => {
            vi.useFakeTimers();
            component.conversation().isFavorite = true;
            component.conversation().isHidden = false;
            fixture.changeDetectorRef.detectChanges();

            const hideButton = fixture.debugElement.nativeElement.querySelector('.hide');
            hideButton.click();
            await vi.advanceTimersByTimeAsync(501);

            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation().id, false);

            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation().id, true);

            expect(component.conversation().isFavorite).toBe(false);
            expect(component.conversation().isHidden).toBe(true);
        });

        it('should remove conversation from hidden when favorited', async () => {
            vi.useFakeTimers();
            component.conversation().isFavorite = false;
            component.conversation().isHidden = true;
            fixture.changeDetectorRef.detectChanges();

            const favoriteButton = fixture.debugElement.nativeElement.querySelector('.favorite');
            favoriteButton.click();
            await vi.advanceTimersByTimeAsync(501);

            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation().id, false);

            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation().id, true);

            expect(component.conversation().isHidden).toBe(false);
            expect(component.conversation().isFavorite).toBe(true);
        });

        it('should call updateIsFavorite when button is clicked', async () => {
            vi.useFakeTimers();
            const button = fixture.debugElement.nativeElement.querySelector('.favorite');
            button.click();
            await vi.advanceTimersByTimeAsync(501);
            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation().id, true);
        });

        it('should call updateIsHidden when button is clicked', async () => {
            vi.useFakeTimers();
            const button = fixture.debugElement.nativeElement.querySelector('.hide');
            button.click();
            await vi.advanceTimersByTimeAsync(501);
            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation().id, true);
        });

        it('should call updateIsMuted when button is clicked', async () => {
            vi.useFakeTimers();
            const button = fixture.debugElement.nativeElement.querySelector('.mute');
            button.click();
            await vi.advanceTimersByTimeAsync(501);
            expect(updateIsMutedSpy).toHaveBeenCalledOnce();
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, component.conversation().id, true);
        });

        it('should open channel overview dialog when button is pressed', async () => {
            if (isOneToOneChatDTO(component.conversation())) {
                // directMessages do not have a channel overview dialog
                return;
            }
            vi.useFakeTimers();
            fixture.detectChanges();
            await vi.advanceTimersByTimeAsync(301);
            const dialogService = TestBed.inject(DialogService);
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: of(undefined) } as any);
            fixture.detectChanges();

            const dialogOpenButton = fixture.debugElement.nativeElement.querySelector('.setting');
            dialogOpenButton.click();
            await vi.advanceTimersByTimeAsync(301);
            await fixture.whenStable();
            expect(openDialogSpy).toHaveBeenCalledOnce();
        });
    });
});
