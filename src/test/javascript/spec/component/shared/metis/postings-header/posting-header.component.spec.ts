import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement, Injector, input, runInInjectionContext } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { getElement } from '../../../../helpers/utils/general.utils';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { PostingHeaderComponent } from 'app/shared/metis/posting-header/posting-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { metisPostExerciseUser1, metisPostLectureUser1, metisResolvingAnswerPostUser1, metisUser1 } from '../../../../helpers/sample/metis-sample-data';
import { UserRole } from 'app/shared/metis/metis.util';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { Posting } from 'app/entities/metis/posting.model';
import { Post } from 'app/entities/metis/post.model';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('PostingHeaderComponent', () => {
    let component: PostingHeaderComponent;
    let fixture: ComponentFixture<PostingHeaderComponent>;
    let debugElement: DebugElement;
    let injector: Injector;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(NgbTooltip)],
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
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(ConfirmIconComponent),
                MockComponent(ProfilePictureComponent),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PostingHeaderComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        injector = fixture.debugElement.injector;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set date information correctly for post of today', () => {
        runInInjectionContext(injector, () => {
            component.posting = input<Posting>(metisPostLectureUser1);
            component.ngOnInit();
            fixture.detectChanges();

            expect(getElement(debugElement, '#today-flag')).toBeDefined();
        });
    });

    it('should not set today flag for posts not created today', () => {
        runInInjectionContext(injector, () => {
            const pastDatePost = {
                ...metisPostLectureUser1,
                creationDate: dayjs().subtract(1, 'day').toDate(),
            } as unknown as Post;
            component.posting = input<Posting>(pastDatePost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(getElement(debugElement, '#today-flag')).toBeNull();
        });
    });

    it('should display resolved icon on resolved post header', () => {
        runInInjectionContext(injector, () => {
            const resolvedPost = { ...metisPostExerciseUser1, resolved: true } as Post;
            component.posting = input<Posting>(resolvedPost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(getElement(debugElement, '.resolved')).not.toBeNull();
        });
    });

    it('should not display resolved icon on unresolved post header', () => {
        runInInjectionContext(injector, () => {
            const unresolvedPost = { ...metisPostExerciseUser1, resolved: false } as Post;
            component.posting = input<Posting>(unresolvedPost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(getElement(debugElement, '.resolved')).toBeNull();
        });
    });

    it.each`
        input                  | expectClass
        ${UserRole.INSTRUCTOR} | ${'post-authority-icon-instructor'}
        ${UserRole.TUTOR}      | ${'post-authority-icon-tutor'}
        ${UserRole.USER}       | ${'post-authority-icon-student'}
    `('should display relevant icon and tooltip for author authority $input', (param: { input: UserRole; expectClass: string }) => {
        runInInjectionContext(injector, () => {
            const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
            component.posting = input<Posting>(rolePost);
            component.ngOnInit();
            fixture.detectChanges();

            const badge = getElement(debugElement, '#role-badge');
            expect(badge).not.toBeNull();
            expect(badge.classList.contains(param.expectClass)).toBeTrue();
        });
    });

    it.each`
        input                  | expectedIcon
        ${UserRole.USER}       | ${faUser}
        ${UserRole.INSTRUCTOR} | ${faUserGraduate}
        ${UserRole.TUTOR}      | ${faUserCheck}
    `('should set userAuthorityIcon correctly for role $input', (param: { input: UserRole; expectedIcon: IconProp }) => {
        runInInjectionContext(injector, () => {
            const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
            component.posting = input<Posting>(rolePost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.userAuthorityIcon).toEqual(param.expectedIcon);
        });
    });

    it.each`
        input                  | expectedTooltip
        ${UserRole.USER}       | ${'artemisApp.metis.userAuthorityTooltips.student'}
        ${UserRole.INSTRUCTOR} | ${'artemisApp.metis.userAuthorityTooltips.instructor'}
        ${UserRole.TUTOR}      | ${'artemisApp.metis.userAuthorityTooltips.tutor'}
    `('should set userAuthorityTooltip correctly for role $input', (param: { input: UserRole; expectedTooltip: string }) => {
        runInInjectionContext(injector, () => {
            const rolePost = { ...metisPostLectureUser1, authorRole: param.input } as Post;
            component.posting = input<Posting>(rolePost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.userAuthorityTooltip).toEqual(param.expectedTooltip);
        });
    });

    it('should set isAuthorOfPosting correctly when user is the author', () => {
        runInInjectionContext(injector, () => {
            const authorPost = { ...metisPostLectureUser1, author: component.currentUser } as Post;
            component.posting = input<Posting>(authorPost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.isAuthorOfPosting).toBeTrue();
        });
    });

    it('should handle undefined posting gracefully', () => {
        runInInjectionContext(injector, () => {
            component.posting = input<Posting>();
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.isPostResolved()).toBeFalse();
            expect(getElement(debugElement, '.resolved')).toBeNull();
        });
    });

    it('should set date information correctly for post of yesterday', () => {
        runInInjectionContext(injector, () => {
            const yesterday = dayjs().subtract(1, 'day').toDate();

            const yesterdayPost: Post = {
                ...metisPostLectureUser1,
                creationDate: yesterday,
            } as unknown as Post;

            component.posting = input<Posting>(yesterdayPost);
            component.ngOnInit();
            fixture.detectChanges();

            expect(getElement(debugElement, '#today-flag')).toBeNull();
        });
    });

    it('should set author information correctly', () => {
        runInInjectionContext(injector, () => {
            component.posting = input<Posting>(metisResolvingAnswerPostUser1);
            const headerAuthorAndDate = getElement(debugElement, '#header-author-date');
            fixture.detectChanges();
            expect(headerAuthorAndDate).not.toBeNull();
            expect(headerAuthorAndDate.innerHTML).toContain(metisUser1.name);
        });
    });
});
