import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { hasLectureUnsavedChangesGuard } from '../../../../../main/webapp/app/lecture/hasLectureUnsavedChanges.guard';
import { LectureUpdateComponent } from '../../../../../main/webapp/app/lecture/lecture-update.component';

describe('hasLectureUnsavedChanges', () => {
    let component: LectureUpdateComponent;
    let currentRoute: ActivatedRouteSnapshot;
    let currentState: RouterStateSnapshot;
    let nextState: RouterStateSnapshot;

    beforeEach(() => {
        component = {
            shouldDisplayDismissWarning: true,
            isShowingWizardMode: false,
            isChangeMadeToTitleSection: jest.fn(),
            isChangeMadeToPeriodSection: jest.fn(),
        } as unknown as LectureUpdateComponent;

        currentRoute = {} as ActivatedRouteSnapshot;
        currentState = {} as RouterStateSnapshot;
        nextState = {} as RouterStateSnapshot;
    });

    it('should return true if dismiss warning shall not be displayed', async () => {
        component.shouldDisplayDismissWarning = false;
        component.isChangeMadeToTitleOrPeriodSection = true;

        const result = await hasLectureUnsavedChangesGuard(component, currentRoute, currentState, nextState).toPromise();
        expect(result).toBeTrue();
    });
});
