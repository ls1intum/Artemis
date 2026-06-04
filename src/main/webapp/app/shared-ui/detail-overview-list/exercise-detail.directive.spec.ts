import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseDetailDirective } from 'app/shared-ui/detail-overview-list/exercise-detail.directive';
import { Component, viewChild } from '@angular/core';
import type {
    BooleanDetail,
    DateDetail,
    Detail,
    LinkDetail,
    NotShownDetail,
    ProgrammingAuxiliaryRepositoryButtonsDetail,
    ProgrammingDiffReportDetail,
    ProgrammingRepositoryButtonsDetail,
    ProgrammingTestStatusDetail,
    ShownDetail,
    TextDetail,
} from 'app/shared-ui/detail-overview-list/detail.model';
import { TextDetailComponent } from 'app/shared-ui/detail-overview-list/components/text-detail/text-detail.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { DateDetailComponent } from 'app/shared-ui/detail-overview-list/components/date-detail/date-detail.component';
import { LinkDetailComponent } from 'app/shared-ui/detail-overview-list/components/link-detail/link-detail.component';
import { BooleanDetailComponent } from 'app/shared-ui/detail-overview-list/components/boolean-detail/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component';
import { ProgrammingAuxiliaryRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component';
import { ProgrammingTestStatusDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { vi } from 'vitest';

@Component({
    template: ` <div jhiExerciseDetail [detail]="detail"></div>`,
    imports: [ExerciseDetailDirective],
})
class TestDetailHostComponent {
    directive = viewChild.required(ExerciseDetailDirective);
    detail: Detail;
}

describe('ExerciseDetailDirective', () => {
    setupTestBed({ zoneless: true });
    let component: TestDetailHostComponent;
    let fixture: ComponentFixture<TestDetailHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestDetailHostComponent, MockDirective(TranslateDirective), MockComponent(TextDetailComponent), MockComponent(ProgrammingDiffReportDetailComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(TestDetailHostComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    /** tests directive for {@link NotShownDetail}s */
    describe('should not create component for NotShownDetails', () => {
        it('detail "false"', () => {
            checkComponentForDetailWasNotCreated(false as NotShownDetail);
        });

        it('detail "undefined"', () => {
            checkComponentForDetailWasNotCreated(undefined as NotShownDetail);
        });
    });

    /** tests directive for {@link ShownDetail}s */
    describe('should create component for ShownDetails', () => {
        it('should create TextDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.Text } as TextDetail, TextDetailComponent);
        });

        it('should create DateDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.Date } as DateDetail, DateDetailComponent);
        });

        it('should create LinkDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.Link } as LinkDetail, LinkDetailComponent);
        });

        it('should create BooleanDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.Boolean } as BooleanDetail, BooleanDetailComponent);
        });

        it('should create ProgrammingRepositoryButtonsDetailComponent component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.ProgrammingRepositoryButtons } as ProgrammingRepositoryButtonsDetail, ProgrammingRepositoryButtonsDetailComponent);
        });

        it('should create ProgrammingAuxiliaryRepositoryButtonsDetailComponent component', () => {
            checkComponentForDetailWasCreated(
                { type: DetailType.ProgrammingAuxiliaryRepositoryButtons } as ProgrammingAuxiliaryRepositoryButtonsDetail,
                ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
            );
        });

        it('should create ProgrammingTestStatusDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.ProgrammingTestStatus } as ProgrammingTestStatusDetail, ProgrammingTestStatusDetailComponent);
        });

        it('should create ProgrammingDiffReportDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.ProgrammingDiffReport } as ProgrammingDiffReportDetail, ProgrammingDiffReportDetailComponent);
        });
    });

    function checkComponentForDetailWasNotCreated(detailToBeChecked: NotShownDetail) {
        const createComponentSpy = vi.spyOn(component.directive().viewContainerRef, 'createComponent');
        component.detail = detailToBeChecked;
        fixture.changeDetectorRef.detectChanges();
        component.directive().ngOnInit();

        expect(createComponentSpy).not.toHaveBeenCalled();
    }

    function checkComponentForDetailWasCreated(detailToBeChecked: ShownDetail, expectedComponent: any) {
        const createComponentSpy = vi.spyOn(component.directive().viewContainerRef, 'createComponent').mockReturnValue({ setInput: vi.fn(), destroy: vi.fn() } as any);
        component.detail = detailToBeChecked;
        fixture.changeDetectorRef.detectChanges();
        component.directive().ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(expectedComponent);
    }
});
