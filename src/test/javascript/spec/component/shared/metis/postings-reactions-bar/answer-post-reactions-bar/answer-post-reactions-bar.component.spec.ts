import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService, TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { By } from '@angular/platform-browser';
import { metisCourse, metisPostInChannel, metisResolvingAnswerPostUser1, metisUser1, post } from '../../../../../helpers/sample/metis-sample-data';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../../helpers/mocks/service/mock-notification.service';
import { provideHttpClient } from '@angular/common/http';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { DebugElement } from '@angular/core';
import { UserRole } from 'app/shared/metis/metis.util';

describe('AnswerPostReactionsBarComponent', () => {
    let component: AnswerPostReactionsBarComponent;
    let fixture: ComponentFixture<AnswerPostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let answerPost: AnswerPost;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;
    let metisServiceUserIsAtLeastTutorMock: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorMock: jest.SpyInstance;
    let metisServiceUserPostingAuthorMock: jest.SpyInstance;
    let metisServiceDeleteAnswerPostMock: jest.SpyInstance;
    let metisServiceUpdateAnswerPostMock: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule), MockModule(NgbTooltipModule)],
            declarations: [
                AnswerPostReactionsBarComponent,
                TranslatePipeMock,
                MockPipe(ReactingUsersOnPostingPipe),
                MockComponent(FaIconComponent),
                MockComponent(EmojiComponent),
                MockComponent(ConfirmIconComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(SessionStorageService),
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostReactionsBarComponent);
                metisService = TestBed.inject(MetisService);
                debugElement = fixture.debugElement;
                metisServiceUserIsAtLeastTutorMock = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserIsAtLeastInstructorMock = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceUserPostingAuthorMock = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceDeleteAnswerPostMock = jest.spyOn(metisService, 'deleteAnswerPost');
                metisServiceUpdateAnswerPostMock = jest.spyOn(metisService, 'updateAnswerPost');
                component = fixture.componentInstance;
                answerPost = new AnswerPost();
                answerPost.id = 1;
                answerPost.author = metisUser1;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = metisUser1;
                reactionToDelete.answerPost = answerPost;
                answerPost.reactions = [reactionToDelete];
                component.posting = answerPost;
                metisService.setCourse(metisCourse);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    function getEditButton(): DebugElement | null {
        return debugElement.query(By.css('button.reaction-button.clickable.px-2.fs-small.edit'));
    }

    function getDeleteButton(): DebugElement | null {
        return debugElement.query(By.css('.delete'));
    }

    function getResolveButton(): DebugElement | null {
        return debugElement.query(By.css('#toggleElement'));
    }

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = jest.spyOn(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.answerPost = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionSpy).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector).toBeFalse();
    });

    it('should display edit and delete options to post author', () => {
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display edit and delete options to instructor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(true);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        component.posting = { ...metisResolvingAnswerPostUser1, post: { ...metisPostInChannel } };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(false);
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        component.posting = { ...metisResolvingAnswerPostUser1, post: { ...metisPostInChannel } };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(false);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(false);
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();

        expect(getDeleteButton()).toBeDefined();
        expect(getDeleteButton()).not.toBeNull();

        const confirmIconComponent = getDeleteButton()!.query(By.directive(ConfirmIconComponent));
        expect(confirmIconComponent).not.toBeNull();
        confirmIconComponent.triggerEventHandler('confirmEvent', null);
        fixture.detectChanges();

        expect(metisServiceDeleteAnswerPostMock).toHaveBeenCalledOnce();
    });

    it('should emit event to create embedded view when edit icon is clicked', () => {
        component.posting = metisResolvingAnswerPostUser1;
        const openPostingCreateEditModalEmitSpy = jest.spyOn(component.openPostingCreateEditModal, 'emit');
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        fixture.detectChanges();
        getElement(debugElement, '.edit').click();
        expect(openPostingCreateEditModalEmitSpy).toHaveBeenCalledOnce();
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = jest.spyOn(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceCreateReactionSpy).toHaveBeenCalledWith(reactionToDelete);
    });

    it('should invoke metis service method with own reaction to remove it', () => {
        component.ngOnInit();
        const addOrRemoveSpy = jest.spyOn(component, 'addOrRemoveReaction');
        component.updateReaction(reactionToDelete.emojiId!);
        expect(addOrRemoveSpy).toHaveBeenCalledWith(reactionToDelete.emojiId!);
    });

    it('answer now button should be invisible if answer is not the last one', () => {
        component.posting = post;
        component.isLastAnswer = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn'));
        expect(answerNowButton).toBeNull();
    });

    it('answer now button should be visible if answer is the last one', () => {
        component.posting = post;
        component.isLastAnswer = true;
        component.ngOnInit();
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('reply');
    });

    it('should invoke metis service when toggle resolve is clicked', () => {
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getResolveButton()).not.toBeNull();
        const previousState = component.posting.resolvesPost;
        component.toggleResolvesPost();
        expect(component.posting.resolvesPost).toEqual(!previousState);
        expect(metisServiceUpdateAnswerPostMock).toHaveBeenCalledOnce();
    });
});
