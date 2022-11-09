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
import { Course } from 'app/entities/course.model';
import { By } from '@angular/platform-browser';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumn {
    @Input() tutorialGroup: TutorialGroup;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-groups-table [tutorialGroups]="tutorialGroups" [course]="course" [showIdColumn]="true">
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
    course: Course;

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
    const course = {
        id: 1,
        title: 'Test Course',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockWrapper,
                MockExtraColumn,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
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
                component.course = course;
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
        expect(tableInstance.course).toEqual(course);
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
    const course = {
        id: 1,
        title: 'Test Course',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
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
                component.course = course;
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
        // get first instance of tutorialGroupRowStubComponent
        const tutorialGroupRowStubComponents = fixture.debugElement.queryAll(By.directive(TutorialGroupRowStubComponent));
        expect(tutorialGroupRowStubComponents).toHaveLength(2);
        tutorialGroupRowStubComponents[0].componentInstance.tutorialGroupClickHandler(tutorialGroupOne);

        expect(tutorialGroupClickHandler).toHaveBeenCalledOnce();
        expect(tutorialGroupClickHandler).toHaveBeenCalledWith(tutorialGroupOne);
    });
});
