import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommunicationService } from 'app/communication/service/communication.service';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { PostingHeaderComponent } from 'app/communication/posting-header/posting-header.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import {
    communicationPostExerciseUser1,
    communicationPostLectureUser1,
    communicationResolvingAnswerPostUser1,
    communicationUser1,
} from 'test/helpers/sample/communication-sample-data';
import { UserRole } from 'app/communication/communication.util';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('PostingHeaderComponent', () => {
    let component: PostingHeaderComponent;
    let fixture: ComponentFixture<PostingHeaderComponent>;
    let debugElement: DebugElement;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                NgbTooltip,
                PostingHeaderComponent,
                FaIconComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                PostingMarkdownEditorComponent,
                PostingButtonComponent,
                ConfirmIconComponent,
                ProfilePictureComponent,
            ],
            providers: [
                FormBuilder,
                { provide: CommunicationService, useClass: MockCommunicationService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(PostingHeaderComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    it.each([
        { isAuthor: true, authorRole: UserRole.USER, active: false },
        { isAuthor: false, authorRole: undefined, active: false },
        { isAuthor: false, authorRole: UserRole.USER, active: true },
    ])('only exposes actionable author names ($isAuthor, $authorRole)', ({ isAuthor, authorRole, active }) => {
        vi.spyOn(TestBed.inject(CommunicationService), 'currentUserIsAuthorOfPosting').mockReturnValue(isAuthor);
        fixture.componentRef.setInput('posting', { ...communicationPostLectureUser1, authorRole });
        fixture.detectChanges();
        const author = fixture.nativeElement.querySelector('#header-author-date .fw-semibold') as HTMLElement;
        const activate = vi.spyOn(component.onUserNameClicked, 'emit');
        expect(author.getAttribute('role')).toBe(active ? 'button' : null);
        expect(author.tabIndex).toBe(active ? 0 : -1);
        const keydown = new KeyboardEvent('keydown', { key: ' ', repeat: true, bubbles: true, cancelable: true });
        author.dispatchEvent(keydown);
        expect(keydown.defaultPrevented).toBe(active);
        expect(activate).not.toHaveBeenCalled();
        const keyup = new KeyboardEvent('keyup', { key: ' ', bubbles: true, cancelable: true });
        author.dispatchEvent(keyup);
        expect(keyup.defaultPrevented).toBe(false);
        expect(activate).toHaveBeenCalledTimes(active ? 1 : 0);
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        author.dispatchEvent(enter);
        expect(enter.defaultPrevented).toBe(false);
        expect(activate).toHaveBeenCalledTimes(active ? 2 : 0);
    });

    it('should set date information correctly for post of today', () => {
        fixture.componentRef.setInput('posting', communicationPostLectureUser1);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeDefined();
    });

    it('should not set today flag for posts not created today', () => {
        const pastDatePost = {
            ...communicationPostLectureUser1,
            creationDate: dayjs().subtract(1, 'day').toDate(),
        } as unknown as Post;
        fixture.componentRef.setInput('posting', pastDatePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeNull();
    });

    it('should display resolved icon on resolved post header', () => {
        const resolvedPost = { ...communicationPostExerciseUser1, resolved: true } as Post;
        fixture.componentRef.setInput('posting', resolvedPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '.resolved')).not.toBeNull();
    });

    it('should not display resolved icon on unresolved post header', () => {
        const unresolvedPost = { ...communicationPostExerciseUser1, resolved: false } as Post;
        fixture.componentRef.setInput('posting', unresolvedPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '.resolved')).toBeNull();
    });

    it.each`
        input                  | expectClass
        ${UserRole.INSTRUCTOR} | ${'post-authority-icon-instructor'}
        ${UserRole.TUTOR}      | ${'post-authority-icon-tutor'}
        ${UserRole.USER}       | ${'post-authority-icon-student'}
    `('should display relevant icon and tooltip for author authority $input', (param: { input: UserRole; expectClass: string }) => {
        const rolePost = { ...communicationPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        const badge = getElement(debugElement, '#role-badge');
        expect(badge).not.toBeNull();
        expect(badge.classList.contains(param.expectClass)).toBe(true);
    });

    it.each`
        input                  | expectedIcon
        ${UserRole.USER}       | ${faUser}
        ${UserRole.INSTRUCTOR} | ${faUserGraduate}
        ${UserRole.TUTOR}      | ${faUserCheck}
    `('should set userAuthorityIcon correctly for role $input', (param: { input: UserRole; expectedIcon: IconProp }) => {
        const rolePost = { ...communicationPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.userAuthorityIcon()).toEqual(param.expectedIcon);
    });

    it.each`
        input                  | expectedTooltip
        ${UserRole.USER}       | ${'artemisApp.communication.userAuthorityTooltips.student'}
        ${UserRole.INSTRUCTOR} | ${'artemisApp.communication.userAuthorityTooltips.instructor'}
        ${UserRole.TUTOR}      | ${'artemisApp.communication.userAuthorityTooltips.tutor'}
    `('should set userAuthorityTooltip correctly for role $input', (param: { input: UserRole; expectedTooltip: string }) => {
        const rolePost = { ...communicationPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.userAuthorityTooltip()).toEqual(param.expectedTooltip);
    });

    it('should set isAuthorOfPosting correctly when user is the author', () => {
        const authorPost = { ...communicationPostLectureUser1, author: component.currentUser() } as Post;
        fixture.componentRef.setInput('posting', authorPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.isAuthorOfPosting()).toBe(true);
    });

    it('should handle undefined posting gracefully', () => {
        fixture.componentRef.setInput('posting', undefined);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.isPostResolved()).toBe(false);
        expect(getElement(debugElement, '.resolved')).toBeNull();
    });

    it('should set date information correctly for post of yesterday', () => {
        const yesterday = dayjs().subtract(1, 'day').toDate();

        const yesterdayPost: Post = {
            ...communicationPostLectureUser1,
            creationDate: yesterday,
        } as unknown as Post;

        fixture.componentRef.setInput('posting', yesterdayPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeNull();
    });

    it('should set author information correctly', () => {
        fixture.componentRef.setInput('posting', communicationResolvingAnswerPostUser1);
        fixture.detectChanges();
        const headerAuthorAndDate = getElement(debugElement, '#header-author-date');
        expect(headerAuthorAndDate).not.toBeNull();
        expect(headerAuthorAndDate.innerHTML).toContain(communicationUser1.name);
    });
});
