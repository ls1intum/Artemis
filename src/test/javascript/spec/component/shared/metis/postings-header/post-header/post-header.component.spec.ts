import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { UserRole } from 'app/shared/metis/metis.util';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { metisAnnouncement, metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { getElement } from '../../../../../helpers/utils/general.utils';

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
                FaIconComponent, // we want to test the type of rendered icons, therefore we cannot mock the component
                MockComponent(PostCreateEditModalComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
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
        expect(getElement(debugElement, '.editIcon')).not.toBeNull();
        expect(getElement(debugElement, '.deleteIcon')).not.toBeNull();
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
        ${UserRole.INSTRUCTOR} | ${'fa fa-user-graduate'}
        ${UserRole.TUTOR}      | ${'fa fa-user-check'}
        ${UserRole.USER}       | ${'fa fa-user'}
    `('should display relevant icon and tooltip for author authority', (param: { input: UserRole; expect: string }) => {
        component.posting = metisAnnouncement;
        component.posting.authorRole = param.input;
        component.ngOnInit();
        fixture.detectChanges();

        // should display relevant icon for author authority
        const icon = getElement(debugElement, 'fa-icon');
        expect(icon).not.toBeNull();
        expect(icon.innerHTML).toInclude(param.expect);
    });
});
