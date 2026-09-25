import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { ExerciseDetailDirective } from 'app/shared-ui/detail-overview-list/exercise-detail.directive';
import { WritableSignal, inputBinding, signal } from '@angular/core';
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
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { DateDetailComponent } from 'app/shared-ui/detail-overview-list/components/date-detail/date-detail.component';
import { LinkDetailComponent } from 'app/shared-ui/detail-overview-list/components/link-detail/link-detail.component';
import { BooleanDetailComponent } from 'app/shared-ui/detail-overview-list/components/boolean-detail/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component';
import { ProgrammingAuxiliaryRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component';
import { ProgrammingTestStatusDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { vi } from 'vitest';

describe('ExerciseDetailDirective', () => {
    let fixture: DirectiveFixture<ExerciseDetailDirective>;
    let detail: WritableSignal<Detail>;

    beforeEach(() => {
        detail = signal<Detail>(undefined);
        fixture = TestBed.createDirective(ExerciseDetailDirective, { tagName: 'div', bindings: [inputBinding('detail', detail)] });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    /** tests directive for {@link NotShownDetail}s */
    describe('should not create component for NotShownDetails', () => {
        it('detail "false"', async () => {
            await checkComponentForDetailWasNotCreated(false as NotShownDetail);
        });

        it('detail "undefined"', async () => {
            await checkComponentForDetailWasNotCreated(undefined as NotShownDetail);
        });
    });

    /** tests directive for {@link ShownDetail}s */
    describe('should create component for ShownDetails', () => {
        it('should create TextDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.Text } as TextDetail, TextDetailComponent);
        });

        it('should create DateDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.Date } as DateDetail, DateDetailComponent);
        });

        it('should create LinkDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.Link } as LinkDetail, LinkDetailComponent);
        });

        it('should create BooleanDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.Boolean } as BooleanDetail, BooleanDetailComponent);
        });

        it('should create ProgrammingRepositoryButtonsDetailComponent component', async () => {
            await checkComponentForDetailWasCreated(
                { type: DetailType.ProgrammingRepositoryButtons } as ProgrammingRepositoryButtonsDetail,
                ProgrammingRepositoryButtonsDetailComponent,
            );
        });

        it('should create ProgrammingAuxiliaryRepositoryButtonsDetailComponent component', async () => {
            await checkComponentForDetailWasCreated(
                { type: DetailType.ProgrammingAuxiliaryRepositoryButtons } as ProgrammingAuxiliaryRepositoryButtonsDetail,
                ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
            );
        });

        it('should create ProgrammingTestStatusDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.ProgrammingTestStatus } as ProgrammingTestStatusDetail, ProgrammingTestStatusDetailComponent);
        });

        it('should create ProgrammingDiffReportDetail component', async () => {
            await checkComponentForDetailWasCreated({ type: DetailType.ProgrammingDiffReport } as ProgrammingDiffReportDetail, ProgrammingDiffReportDetailComponent);
        });
    });

    async function checkComponentForDetailWasNotCreated(detailToBeChecked: NotShownDetail) {
        const createComponentSpy = vi.spyOn(fixture.directiveInstance.viewContainerRef, 'createComponent');
        detail.set(detailToBeChecked);
        fixture.detectChanges();
        await fixture.directiveInstance.ngOnInit();

        expect(createComponentSpy).not.toHaveBeenCalled();
    }

    async function checkComponentForDetailWasCreated(detailToBeChecked: ShownDetail, expectedComponent: any) {
        const createComponentSpy = vi.spyOn(fixture.directiveInstance.viewContainerRef, 'createComponent').mockReturnValue({ setInput: vi.fn(), destroy: vi.fn() } as any);
        detail.set(detailToBeChecked);
        fixture.detectChanges();
        await fixture.directiveInstance.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(expectedComponent);
    }
});
