import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { metisAnswerPostUser1 } from '../../../../../helpers/sample/metis-sample-data';

describe('AnswerPostFooterComponent', () => {
    let component: AnswerPostFooterComponent;
    let fixture: ComponentFixture<AnswerPostFooterComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityMock: jest.SpyInstance;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [AnswerPostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(AnswerPostReactionsBarComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostFooterComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityMock = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = metisAnswerPostUser1;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        jest.restoreAllMocks();
    });

    it('should initialize user authority and answer post footer correctly', () => {
        metisServiceUserAuthorityMock.mockReturnValue(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBe(false);
        fixture.detectChanges();
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).toBeDefined();
    });

    it('should initialize user authority and answer post footer correctly', () => {
        metisServiceUserAuthorityMock.mockReturnValue(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBe(true);
        fixture.detectChanges();
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).toBeDefined();
    });

    it('should toggle answer post from unapproved to approved on click', () => {
        const toggleApproveSpy = jest.spyOn(component, 'toggleApprove');
        metisServiceUserAuthorityMock.mockReturnValue(true);
        fixture.detectChanges();
        const toggleElement = getElement(debugElement, '#toggleElement');
        toggleElement.click();
        fixture.detectChanges();
        expect(toggleApproveSpy).toHaveBeenCalled();
        expect(component.posting.tutorApproved).toBe(true);
        const approvedBadge = getElement(debugElement, '.approved-badge');
        expect(approvedBadge).toBeDefined();
    });
});
