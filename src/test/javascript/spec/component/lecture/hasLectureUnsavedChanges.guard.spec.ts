import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { hasLectureUnsavedChangesGuard } from '../../../../../main/webapp/app/lecture/hasLectureUnsavedChanges.guard';
import { LectureUpdateComponent } from '../../../../../main/webapp/app/lecture/lecture-update.component';
import { TestBed } from '@angular/core/testing';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

describe('hasLectureUnsavedChanges', () => {
    let component: LectureUpdateComponent;
    let currentRoute: ActivatedRouteSnapshot;
    let currentState: RouterStateSnapshot;
    let nextState: RouterStateSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateComponent],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                {
                    provide: LectureUpdateComponent,
                    useValue: {
                        shouldDisplayDismissWarning: true,
                        isShowingWizardMode: false,
                        isChangeMadeToTitleSection: jest.fn().mockReturnValue(true),
                        isChangeMadeToPeriodSection: jest.fn().mockReturnValue(true),
                        isChangeMadeToTitleOrPeriodSection: true,
                    },
                },
            ],
        }).compileComponents();

        component = TestBed.inject(LectureUpdateComponent);
        currentRoute = {} as ActivatedRouteSnapshot;
        currentState = {} as RouterStateSnapshot;
        nextState = {} as RouterStateSnapshot;
    });

    it('should return true if warning is not bypassed by shouldDisplayDismissWarning variable but no changes were made', async () => {
        component.shouldDisplayDismissWarning = true;
        component.isChangeMadeToTitleOrPeriodSection = false;

        const result = await hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState).toPromise();
        expect(result).toBeTrue();
    });

    it('should return true if dismiss warning shall not be displayed', async () => {
        component.shouldDisplayDismissWarning = false;
        component.isChangeMadeToTitleOrPeriodSection = true;

        const result = await hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState).toPromise();
        expect(result).toBeTrue();
    });
});
