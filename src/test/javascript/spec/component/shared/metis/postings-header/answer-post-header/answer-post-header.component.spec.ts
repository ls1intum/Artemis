import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, ViewContainerRef } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { MockViewContainerRef } from '../../../../../helpers/mocks/service/mock-view-container-ref.service';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { metisAnswerPostUser2, metisPostInChannel, metisResolvingAnswerPostUser1, metisUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { UserRole } from 'app/shared/metis/metis.util';

describe('AnswerPostHeaderComponent', () => {
    let component: AnswerPostHeaderComponent;
    let fixture: ComponentFixture<AnswerPostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorMock: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorMock: jest.SpyInstance;
    let metisServiceUserPostingAuthorMock: jest.SpyInstance;
    let metisServiceDeleteAnswerPostMock: jest.SpyInstance;
    let metisServiceUpdateAnswerPostMock: jest.SpyInstance;

    const yesterday: dayjs.Dayjs = dayjs().subtract(1, 'day');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(NgbTooltip)],
            providers: [
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                {
                    provide: NgbModal,
                    useClass: MockNgbModalService,
                },
                { provide: ViewContainerRef, useClass: MockViewContainerRef },
            ],
            declarations: [
                AnswerPostHeaderComponent,
                AnswerPostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(FaIconComponent),
                MockComponent(ConfirmIconComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserIsAtLeastTutorMock = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserIsAtLeastInstructorMock = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceUserPostingAuthorMock = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceDeleteAnswerPostMock = jest.spyOn(metisService, 'deleteAnswerPost');
                metisServiceUpdateAnswerPostMock = jest.spyOn(metisService, 'updateAnswerPost');
                debugElement = fixture.debugElement;
                component.posting = metisResolvingAnswerPostUser1;
                component.posting.creationDate = yesterday;
                component.ngOnInit();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set author information correctly', () => {
        fixture.detectChanges();
        const headerAuthorAndDate = getElement(debugElement, '.posting-header.header-author-date');
        expect(headerAuthorAndDate).not.toBeNull();
        expect(headerAuthorAndDate.innerHTML).toContain(metisUser1.name);
    });

    it('should set date information correctly for post of yesterday', () => {
        component.posting.creationDate = yesterday;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.today-flag')).toBeNull();
    });

    it('should display edit and delete options to post author', () => {
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it('should display edit and delete options to instructor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(true);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        component.posting = { ...metisResolvingAnswerPostUser1, post: { ...metisPostInChannel } };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(false);
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        component.posting = { ...metisResolvingAnswerPostUser1, post: { ...metisPostInChannel } };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).toBeNull();
    });

    it('should initialize answer post as marked as resolved', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(false);
        fixture.detectChanges();
        expect(component.posting.resolvesPost).toBeTruthy();
        expect(getElement(debugElement, '.resolved')).not.toBeNull();
        expect(getElement(debugElement, '.notResolved')).toBeNull();
    });

    it('should initialize answer post not marked as resolved but show the check to mark it as such', () => {
        // tutors should see the check to mark an answer post as resolving
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        // answer post that is not resolving original post
        component.posting = metisAnswerPostUser2;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.resolved')).toBeNull();
        expect(getElement(debugElement, '.notResolved')).not.toBeNull();
    });

    it('should initialize answer post not marked as resolved and not show the check to mark it as such', () => {
        // user, that is not author of original post, should not see the check to mark an answer post as resolving
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(false);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        // answer post that is not resolving original post
        component.posting = metisAnswerPostUser2;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.resolved')).toBeNull();
        expect(getElement(debugElement, '.notResolved')).toBeNull();
    });

    it('should initialize as tutor answer post not marked as resolved but show the check to mark it as such', () => {
        // user, that is the author of original post, should not see the check to mark an answer post as resolving
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(false);
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        // answer post that is not resolving original post
        component.posting = metisAnswerPostUser2;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.resolved')).toBeNull();
        expect(getElement(debugElement, '.notResolved')).not.toBeNull();
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(false);
        metisServiceUserPostingAuthorMock.mockReturnValue(false);
        metisServiceUserIsAtLeastInstructorMock.mockReturnValue(false);
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).toBeNull();
    });

    it('should emit event to create embedded view when edit icon is clicked', () => {
        component.posting = metisResolvingAnswerPostUser1;
        const openPostingCreateEditModalEmitSpy = jest.spyOn(component.openPostingCreateEditModal, 'emit');
        metisServiceUserPostingAuthorMock.mockReturnValue(true);
        fixture.detectChanges();
        getElement(debugElement, '.editIcon').click();
        expect(openPostingCreateEditModalEmitSpy).toHaveBeenCalledOnce();
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
        component.deletePosting();
        expect(metisServiceDeleteAnswerPostMock).toHaveBeenCalledOnce();
    });

    it('should invoke metis service when toggle resolve is clicked as tutor', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        fixture.detectChanges();
        const resolveIcon = getElement(debugElement, '.resolved');
        expect(resolveIcon).not.toBeNull();
        const previousState = component.posting.resolvesPost;
        component.toggleResolvesPost();
        expect(component.posting.resolvesPost).toEqual(!previousState);
        expect(metisServiceUpdateAnswerPostMock).toHaveBeenCalledOnce();
    });

    it('should invoke metis service when toggle resolve is clicked as post author', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
        component.deletePosting();
        expect(metisServiceDeleteAnswerPostMock).toHaveBeenCalledOnce();
    });
});
