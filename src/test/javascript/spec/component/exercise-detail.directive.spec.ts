import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseDetailDirective } from 'app/detail-overview-list/exercise-detail.directive';
import { Component, ViewChild } from '@angular/core';
import type {
    BooleanDetail,
    DateDetail,
    Detail,
    LinkDetail,
    NotShownDetail,
    ProgrammingRepositoryButtonsDetail,
    ShownDetail,
    TextDetail,
} from 'app/detail-overview-list/detail.model';
import { TextDetailComponent } from 'app/detail-overview-list/components/text-detail.component';
import { MockComponent } from 'ng-mocks';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { DateDetailComponent } from 'app/detail-overview-list/components/date-detail.component';
import { LinkDetailComponent } from 'app/detail-overview-list/components/link-detail.component';
import { BooleanDetailComponent } from 'app/detail-overview-list/components/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/detail-overview-list/components/programming-repository-buttons-detail.component';

@Component({
    template: `<div jhiExerciseDetail [detail]="detail"></div>`,
})
class TestDetailHostComponent {
    @ViewChild(ExerciseDetailDirective) directive: ExerciseDetailDirective;
    detail: Detail;
}

describe('ExerciseDetailDirective', () => {
    let component: TestDetailHostComponent;
    let fixture: ComponentFixture<TestDetailHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TestDetailHostComponent, ExerciseDetailDirective, MockComponent(TextDetailComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(TestDetailHostComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.resetAllMocks();
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
    });

    function checkComponentForDetailWasNotCreated(detailToBeChecked: NotShownDetail) {
        const createComponentSpy = jest.spyOn(component.directive.viewContainerRef, 'createComponent');
        component.detail = detailToBeChecked;
        fixture.detectChanges();
        component.directive.ngOnInit();

        expect(createComponentSpy).not.toHaveBeenCalled();
    }

    function checkComponentForDetailWasCreated(detailToBeChecked: ShownDetail, expectedComponent: any) {
        const createComponentSpy = jest.spyOn(component.directive.viewContainerRef, 'createComponent');
        component.detail = detailToBeChecked;
        fixture.detectChanges();
        component.directive.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(expectedComponent);
    }
});
