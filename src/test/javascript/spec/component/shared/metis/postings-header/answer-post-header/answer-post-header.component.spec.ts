import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MetisModule } from 'app/shared/metis/metis.module';
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
import { metisAnswerPostUser2, metisResolvingAnswerPostUser1, metisUser1 } from '../../../../../helpers/sample/metis-sample-data';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';

describe('AnswerPostHeaderComponent', () => {
    let component: AnswerPostHeaderComponent;
    let fixture: ComponentFixture<AnswerPostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorMock: jest.SpyInstance;
    let metisServiceUserPostingAuthorMock: jest.SpyInstance;

    const yesterday: dayjs.Dayjs = dayjs().subtract(1, 'day');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(NgbTooltip), MockModule(MetisModule)],
            providers: [
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                {
                    provide: NgbModal,
                    useClass: MockNgbModalService,
                },
                { provide: ViewContainerRef, useClass: MockViewContainerRef },
                { provide: AccountService, useClass: MockAccountService },
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
                metisServiceUserPostingAuthorMock = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
                debugElement = fixture.debugElement;
                component.posting = metisResolvingAnswerPostUser1;
                component.posting.creationDate = yesterday;
                component.ngOnInit();
                component.userRoleBadge = 'artemisApp.metis.userRoles.student';
                component.todayFlag = 'artemisApp.metis.today';
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display default profile picture', () => {
        fixture.detectChanges();
        expect(getElement(debugElement, '#post-default-profile-picture')).not.toBeNull();
    });

    it('should set author information correctly', () => {
        fixture.detectChanges();
        const headerAuthorAndDate = getElement(debugElement, '#header-author-date');
        expect(headerAuthorAndDate).not.toBeNull();
        expect(headerAuthorAndDate.innerHTML).toContain(metisUser1.name);
    });

    it('should set date information correctly for post of yesterday', () => {
        component.posting.creationDate = yesterday;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '#today-flag')).toBeNull();
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
});
