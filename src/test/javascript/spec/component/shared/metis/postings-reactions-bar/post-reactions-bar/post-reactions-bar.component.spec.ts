import { ComponentFixture, getTestBed, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { metisUser1 } from '../../../../../helpers/sample/metis-sample-data';

describe('PostReactionsBarComponent', () => {
    let component: PostReactionsBarComponent;
    let injector: TestBed;
    let fixture: ComponentFixture<PostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let accountService: MockAccountService;
    let accountServiceAuthorityMock: jest.SpyInstance;
    let metisServiceUpdateDisplayPriorityMock: jest.SpyInstance;
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule)],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            declarations: [PostReactionsBarComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgbTooltip), MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostReactionsBarComponent);
                injector = getTestBed();
                metisService = injector.get(MetisService);
                accountService = injector.get(AccountService);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                accountServiceAuthorityMock = jest.spyOn(accountService, 'isAtLeastTutorInCourse');
                metisServiceUpdateDisplayPriorityMock = jest.spyOn(metisService, 'updatePostDisplayPriority');
                post = new Post();
                post.id = 1;
                post.author = metisUser1;
                post.displayPriority = DisplayPriority.NONE;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = metisUser1;
                reactionToDelete.post = post;
                post.reactions = [reactionToDelete];
                component.posting = post;
            });
    });

    afterEach(function () {
        jest.clearAllMocks();
    });

    it('should initialize user authority and reactions correctly', () => {
        accountServiceAuthorityMock.mockReturnValue(false);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).toEqual(false);
        fixture.detectChanges();
        const reaction = getElement(debugElement, 'ngx-emoji');
        expect(reaction).toBeDefined();
        expect(component.reactionCountMap).toEqual({
            smile: {
                count: 1,
                hasReacted: false,
            },
        });
    });

    it('should initialize user authority and reactions correctly with same user', () => {
        component.posting!.author!.id = 99;
        accountServiceAuthorityMock.mockReturnValue(true);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).toEqual(true);
        fixture.detectChanges();
        const reactions = getElements(debugElement, 'ngx-emoji');
        // emojis to be displayed it the user reaction, the pin emoji and the archive emoji
        expect(reactions).toHaveLength(3);
        expect(component.reactionCountMap).toEqual({
            smile: {
                count: 1,
                hasReacted: true,
            },
        });
        // set correct tooltips for tutor and post that is not pinned and not archived
        expect(component.archiveTooltip).toEqual('artemisApp.metis.archivePostTutorTooltip');
        expect(component.pinTooltip).toEqual('artemisApp.metis.pinPostTutorTooltip');
    });

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionMock = jest.spyOn(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.post = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionMock).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceDeleteReactionMock = jest.spyOn(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceDeleteReactionMock).toHaveBeenCalledWith(reactionToDelete);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method when pin icon is toggled', () => {
        accountServiceAuthorityMock.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin');
        pinEmoji.click();
        component.posting.displayPriority = DisplayPriority.PINNED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting.id!, DisplayPriority.PINNED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is pinned and not archived
        expect(component.pinTooltip).toEqual('artemisApp.metis.removePinPostTutorTooltip');
        expect(component.archiveTooltip).toEqual('artemisApp.metis.archivePostTutorTooltip');
    });

    it('should invoke metis service method when archive icon is toggled', () => {
        accountServiceAuthorityMock.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        const archiveEmoji = getElement(debugElement, '.archive');
        archiveEmoji.click();
        component.posting.displayPriority = DisplayPriority.ARCHIVED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting.id!, DisplayPriority.ARCHIVED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is archived and not pinned
        expect(component.pinTooltip).toEqual('artemisApp.metis.pinPostTutorTooltip');
        expect(component.archiveTooltip).toEqual('artemisApp.metis.removeArchivePostTutorTooltip');
    });

    it('should show non-clickable pin emoji with correct tooltip for student when post is pinned', () => {
        accountServiceAuthorityMock.mockReturnValue(false);
        component.posting.displayPriority = DisplayPriority.PINNED;
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin.reaction-button--not-hoverable');
        expect(pinEmoji).toBeDefined();
        pinEmoji.click();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is pinned
        expect(component.pinTooltip).toEqual('artemisApp.metis.pinnedPostTooltip');
    });

    it('should show non-clickable archive emoji with correct tooltip for student when post is archived', () => {
        accountServiceAuthorityMock.mockReturnValue(false);
        component.posting.displayPriority = DisplayPriority.ARCHIVED;
        component.ngOnInit();
        fixture.detectChanges();
        const archiveEmoji = getElement(debugElement, '.archive.reaction-button--not-hoverable');
        expect(archiveEmoji).toBeDefined();
        archiveEmoji.click();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is archived
        expect(component.archiveTooltip).toEqual('artemisApp.metis.archivedPostTooltip');
    });
});
