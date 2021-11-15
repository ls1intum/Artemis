import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorMock: jest.SpyInstance;
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
                MockComponent(FaIconComponent),
                MockComponent(ConfirmIconComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserIsAtLeastTutorMock = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
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

    it('should display edit and delete options to tutor', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).toBeDefined();
        expect(getElement(debugElement, '.deleteIcon')).toBeDefined();
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorMock.mockReturnValue(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).toBeDefined();
        component.deletePosting();
        expect(metisServiceDeletePostMock).toHaveBeenCalled();
    });
});
