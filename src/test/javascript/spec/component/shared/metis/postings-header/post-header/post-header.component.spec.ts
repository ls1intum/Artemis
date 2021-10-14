import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { metisAnswerPosts, metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';

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
                MockComponent(PostingsMarkdownEditorComponent),
                MockComponent(PostingsButtonComponent),
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

    it('should have information on answers on init', () => {
        expect(component.numberOfAnswerPosts).toBeDefined();
    });

    it('should have information on answers on changes', () => {
        component.ngOnChanges();
        expect(component.numberOfAnswerPosts).toBeDefined();
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

    it('should not show answer-count icon for post without answers', () => {
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).toEqual(0);
        expect(getElement(debugElement, '.answer-count')).toBeNull();
        expect(getElement(debugElement, '.answer-count .icon')).toBeDefined();
    });

    it('should only display non clickable icon for post with answers', () => {
        component.posting.answers = metisAnswerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).toEqual(metisAnswerPosts.length);
        expect(getElement(debugElement, '.answer-count').innerHTML).toContain(String(metisAnswerPosts.length));
        expect(getElement(debugElement, '.answer-count .icon')).toBeDefined();
        expect(getElement(debugElement, '.toggle-answer-element.clickable')).toBeDefined();
    });

    it('should call toggleAnswers method and emit event when answer count icon is clicked', () => {
        const toggleAnswersSpy = jest.spyOn(component, 'toggleAnswers');
        const toggleAnswersChangeSpy = jest.spyOn(component.toggleAnswersChange, 'emit');
        component.posting.answers = metisAnswerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        getElement(debugElement, '.toggle-answer-element.clickable').click();
        expect(toggleAnswersSpy).toHaveBeenCalled();
        expect(toggleAnswersChangeSpy).toHaveBeenCalled();
    });
});
