import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { metisAnnouncement, metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { Authority } from 'app/shared/constants/authority.constants';
import { HtmlForPostingMarkdownPipe } from 'app/shared/pipes/html-for-posting-markdown.pipe';

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorStub: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceDeletePostMock: jest.SpyInstance;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostHeaderComponent,
                MockComponent(PostCreateEditModalComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                HtmlForPostingMarkdownPipe, // we want to test against the rendered string, therefore we cannot mock the pipe
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
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
        expect(getElement(debugElement, '.today-flag')).toBeDefined();
    });

    it('should display edit and delete options to tutor if posting is not announcement', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBe(null);
        expect(getElement(debugElement, '.deleteIcon')).not.toBe(null);
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).not.toBe(null);
        component.deletePosting();
        expect(metisServiceDeletePostMock).toHaveBeenCalledOnce();
    });

    it('should not display edit and delete options to tutor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBe(null);
        expect(getElement(debugElement, '.deleteIcon')).not.toBe(null);
    });

    it('should display edit and delete options to instructor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).not.toBe(null);
        expect(getElement(debugElement, '.deleteIcon')).not.toBe(null);
    });

    it('should display relevant icon and tooltip if author is an instructor', () => {
        component.posting = metisAnnouncement;
        // @ts-ignore
        component.posting.author = { id: 4, name: 'username4', login: 'login4', groups: ['metisInstructors'], authorities: [{ name: Authority.INSTRUCTOR }] };
        component.ngOnInit();
        fixture.detectChanges();

        // should display relevant icon for author authority
        const icon = getElement(debugElement, 'fa-icon');
        expect(icon).not.toBe(null);
        expect(icon.innerHTML).toInclude('fa fa-user-graduate');
    });
});
