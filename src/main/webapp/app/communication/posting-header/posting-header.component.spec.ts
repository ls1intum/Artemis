import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { PostingHeaderComponent } from 'app/communication/posting-header/posting-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { metisPostExerciseUser1, metisPostLectureUser1, metisResolvingAnswerPostUser1, metisUser1 } from 'test/helpers/sample/metis-sample-data';
import { UserRole } from 'app/communication/metis.util';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule), NgbTooltip],
            providers: [
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            declarations: [
                PostingHeaderComponent,
                FaIconComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                PostingMarkdownEditorComponent,
                PostingButtonComponent,
                ConfirmIconComponent,
                ProfilePictureComponent,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PostingHeaderComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set date information correctly for post of today', () => {
        fixture.componentRef.setInput('posting', metisPostLectureUser1);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeDefined();
    });

    it('should not set today flag for posts not created today', () => {
        const pastDatePost = {
            ...metisPostLectureUser1,
            creationDate: dayjs().subtract(1, 'day').toDate(),
        } as unknown as Post;
        fixture.componentRef.setInput('posting', pastDatePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeNull();
    });

    it('should display resolved icon on resolved post header', () => {
        const resolvedPost = { ...metisPostExerciseUser1, resolved: true } as Post;
        fixture.componentRef.setInput('posting', resolvedPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '.resolved')).not.toBeNull();
    });

    it('should not display resolved icon on unresolved post header', () => {
        const unresolvedPost = { ...metisPostExerciseUser1, resolved: false } as Post;
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
        const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        const badge = getElement(debugElement, '#role-badge');
        expect(badge).not.toBeNull();
        expect(badge.classList.contains(param.expectClass)).toBeTrue();
    });

    it.each`
        input                  | expectedIcon
        ${UserRole.USER}       | ${faUser}
        ${UserRole.INSTRUCTOR} | ${faUserGraduate}
        ${UserRole.TUTOR}      | ${faUserCheck}
    `('should set userAuthorityIcon correctly for role $input', (param: { input: UserRole; expectedIcon: IconProp }) => {
        const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.userAuthorityIcon).toEqual(param.expectedIcon);
    });

    it.each`
        input                  | expectedTooltip
        ${UserRole.USER}       | ${'artemisApp.metis.userAuthorityTooltips.student'}
        ${UserRole.INSTRUCTOR} | ${'artemisApp.metis.userAuthorityTooltips.instructor'}
        ${UserRole.TUTOR}      | ${'artemisApp.metis.userAuthorityTooltips.tutor'}
    `('should set userAuthorityTooltip correctly for role $input', (param: { input: UserRole; expectedTooltip: string }) => {
        const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
        fixture.componentRef.setInput('posting', rolePost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.userAuthorityTooltip).toEqual(param.expectedTooltip);
    });

    it('should set isAuthorOfPosting correctly when user is the author', () => {
        const authorPost = { ...metisPostLectureUser1, author: component.currentUser } as Post;
        fixture.componentRef.setInput('posting', authorPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.isAuthorOfPosting).toBeTrue();
    });

    it('should handle undefined posting gracefully', () => {
        fixture.componentRef.setInput('posting', undefined);
        fixture.detectChanges();
        component.ngOnInit();

        expect(component.isPostResolved()).toBeFalse();
        expect(getElement(debugElement, '.resolved')).toBeNull();
    });

    it('should set date information correctly for post of yesterday', () => {
        const yesterday = dayjs().subtract(1, 'day').toDate();

        const yesterdayPost: Post = {
            ...metisPostLectureUser1,
            creationDate: yesterday,
        } as unknown as Post;

        fixture.componentRef.setInput('posting', yesterdayPost);
        fixture.detectChanges();
        component.ngOnInit();

        expect(getElement(debugElement, '#today-flag')).toBeNull();
    });

    it('should set author information correctly', () => {
        fixture.componentRef.setInput('posting', metisResolvingAnswerPostUser1);
        fixture.detectChanges();
        const headerAuthorAndDate = getElement(debugElement, '#header-author-date');
        expect(headerAuthorAndDate).not.toBeNull();
        expect(headerAuthorAndDate.innerHTML).toContain(metisUser1.name);
    });
});
