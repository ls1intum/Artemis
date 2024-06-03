import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseDetailDirective } from 'app/detail-overview-list/exercise-detail.directive';
import { Component, ViewChild } from '@angular/core';
import { Detail, NotShownDetail, ShownDetail, TextDetail } from 'app/detail-overview-list/detail.model';
import { TextDetailComponent } from 'app/detail-overview-list/components/text-detail.component';
import { MockComponent } from 'ng-mocks';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';

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

    describe('should not create component for NotShownDetails', () => {
        it('detail "false"', () => {
            checkComponentForDetailWasNotCreated(false as NotShownDetail);
        });

        it('detail "undefined"', () => {
            checkComponentForDetailWasNotCreated(undefined as NotShownDetail);
        });
    });

    describe('should create component for ShownDetails', () => {
        it('should create TextDetail component', () => {
            checkComponentForDetailWasCreated({ type: DetailType.Text } as TextDetail, TextDetailComponent);
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
