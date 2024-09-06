import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MetisModule } from 'app/shared/metis/metis.module';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { metisAnnouncement, metisPostExerciseUser1, metisPostInChannel, metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { UserRole } from 'app/shared/metis/metis.util';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorStub: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceUserIsAuthorOfPostingStub: jest.SpyInstance;
    let metisServiceDeletePostMock: jest.SpyInstance;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(NgbTooltip), MockModule(MetisModule)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }, { provide: AccountService, useClass: MockAccountService }],
            declarations: [
                PostHeaderComponent,
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
                MockComponent(PostCreateEditModalComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(ConfirmIconComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserIsAtLeastTutorStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserIsAtLeastInstructorStub = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceUserIsAuthorOfPostingStub = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceDeletePostMock = jest.spyOn(metisService, 'deletePost');
                debugElement = fixture.debugElement;
                component.posting = metisPostLectureUser1;
                component.ngOnInit();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set date information correctly for post of today', () => {
        fixture.detectChanges();
        expect(getElement(debugElement, '#today-flag')).toBeDefined();
    });

    it('should display default profile picture', () => {
        fixture.detectChanges();
        expect(getElement(debugElement, '#post-default-profile-picture')).not.toBeNull();
    });

    it('should display resolved icon on resolved post header', () => {
        component.posting = metisPostExerciseUser1;
        component.posting.resolved = true;

        component.ngOnInit();
        fixture.detectChanges();

        expect(getElement(debugElement, '.resolved')).not.toBeNull();
    });

    it('should display edit and delete options to tutor if posting is not announcement', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it('should display edit and delete options to instructor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        component.posting = { ...metisPostInChannel };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is in course-wide channel', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        component.posting = { ...metisPostInChannel };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).toBeNull();
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
        component.deletePosting();
        expect(metisServiceDeletePostMock).toHaveBeenCalledOnce();
    });

    it('should not display edit and delete options to tutor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it('should display edit and delete options to instructor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it.each`
        input                  | expect
        ${UserRole.INSTRUCTOR} | ${'post-authority-icon-instructor'}
        ${UserRole.TUTOR}      | ${'post-authority-icon-tutor'}
        ${UserRole.USER}       | ${'post-authority-icon-student'}
    `('should display relevant icon and tooltip for author authority', (param: { input: UserRole; expect: string }) => {
        component.posting = metisAnnouncement;
        component.posting.authorRole = param.input;
        component.ngOnInit();
        fixture.detectChanges();

        // should display relevant icon for author authority
        const badge = getElement(debugElement, '#role-badge');
        expect(badge).not.toBeNull();
        expect(badge.classList.contains(param.expect)).toBeTrue();
    });
});
