import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupsTableComponent } from 'app/course/tutorial-groups/shared/tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { Component, Input, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { TutorialGroupRowStubComponent } from '../stubs/tutorial-groups-table-stub.component';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumn {
    @Input() tutorialGroup: TutorialGroup;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-groups-table [tutorialGroups]="tutorialGroups" [courseId]="courseId" [showIdColumn]="true">
            <ng-template let-tutorialGroup>
                <jhi-mock-extra-column [tutorialGroup]="tutorialGroup"></jhi-mock-extra-column>
            </ng-template>
        </jhi-tutorial-groups-table>
    `,
})
class MockWrapper {
    @Input()
    tutorialGroups: TutorialGroup[];

    @Input()
    courseId: number;

    @ViewChild(TutorialGroupsTableComponent)
    tutorialGroupTableInstance: TutorialGroupsTableComponent;

    @ViewChildren(MockExtraColumn)
    mockExtraColumns: QueryList<MockExtraColumn>;
}

describe('TutorialGroupTableWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapper>;
    let component: MockWrapper;
    let tableInstance: TutorialGroupsTableComponent;
    let mockExtraColumns: MockExtraColumn[];
    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockWrapper,
                MockExtraColumn,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
            ],
            providers: [MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapper);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
                component.courseId = 1;
                fixture.detectChanges();
                tableInstance = component.tutorialGroupTableInstance;
                mockExtraColumns = component.mockExtraColumns.toArray();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the headers', () => {
        expect(tableInstance.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tableInstance.courseId).toBe(1);
        expect(mockExtraColumns).toHaveLength(2);
        mockExtraColumns.sort((a, b) => a.tutorialGroup!.id! - b.tutorialGroup!.id!);
        expect(mockExtraColumns[0].tutorialGroup).toEqual(tutorialGroupOne);
        expect(mockExtraColumns[1].tutorialGroup).toEqual(tutorialGroupTwo);
        expect(fixture.nativeElement.querySelectorAll('jhi-mock-extra-column')).toHaveLength(2);
    });
});

describe('TutorialGroupsTableComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsTableComponent>;
    let component: TutorialGroupsTableComponent;

    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsTableComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
            ],
            providers: [MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsTableComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
                component.courseId = 1;
                component.showIdColumn = true;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should call sort service', () => {
        fixture.detectChanges();
        component.sortingPredicate = 'id';
        component.ascending = false;

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], 'id', false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call tutorialGroupClickHandler', () => {
        fixture.detectChanges();
        const tutorialGroupClickHandler = jest.fn();
        component.tutorialGroupClickHandler = tutorialGroupClickHandler;
        fixture.detectChanges();
        const courseLink = fixture.debugElement.nativeElement.querySelector('#id-1');
        courseLink.click();
        expect(tutorialGroupClickHandler).toHaveBeenCalledOnce();
        expect(tutorialGroupClickHandler).toHaveBeenCalledWith(tutorialGroupOne);
    });
});
