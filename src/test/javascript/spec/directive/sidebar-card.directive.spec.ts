import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, ViewChild } from '@angular/core';
import { MockComponent } from 'ng-mocks';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SidebarCardDirective } from 'app/shared/sidebar/sidebar-card.directive';
import { MockRouter } from '../helpers/mocks/mock-router';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';

@Component({
    template: `<div jhiSidebarCard [size]="size" [itemSelected]="false"></div>`,
})
class TestHostComponent {
    @ViewChild(SidebarCardDirective) directive: SidebarCardDirective;
    size: string;
}

describe('SidebarCardDirective', () => {
    let component: TestHostComponent;
    let fixture: ComponentFixture<TestHostComponent>;
    let queryParamsSubject: BehaviorSubject<Params>;
    const router = new MockRouter();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                TestHostComponent,
                SidebarCardDirective,
                MockComponent(SidebarCardSmallComponent),
                MockComponent(SidebarCardMediumComponent),
                MockComponent(SidebarCardLargeComponent),
            ],
            providers: [
                { provide: Router, useValue: router },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: new BehaviorSubject(
                                    convertToParamMap({
                                        courseId: 5,
                                    }),
                                ),
                            },
                        },
                        queryParams: queryParamsSubject,
                    },
                },
            ],
        }).compileComponents();

        jest.spyOn(console, 'warn').mockImplementation(() => {});

        fixture = TestBed.createComponent(TestHostComponent);
        component = fixture.componentInstance;
        TestBed.inject(ActivatedRoute);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should create an instance', () => {
        expect(component.directive).toBeDefined();
    });

    it('directive and viewContainerRef should be defined', () => {
        expect(component.directive).toBeDefined();
        expect(component.directive.viewContainerRef).toBeDefined();
    });

    it('should create SidebarCardSmallComponent when size is "S"', () => {
        const createComponentSpy = jest.spyOn(component.directive.viewContainerRef, 'createComponent');
        component.size = 'S';
        fixture.detectChanges();
        component.directive.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(SidebarCardSmallComponent);
    });

    it('should create SidebarCardSmallComponent when size is "M"', () => {
        const createComponentSpy = jest.spyOn(component.directive.viewContainerRef, 'createComponent');
        component.size = 'M';
        fixture.detectChanges();
        component.directive.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(SidebarCardMediumComponent);
    });

    it('should create SidebarCardSmallComponent when size is "L"', () => {
        const createComponentSpy = jest.spyOn(component.directive.viewContainerRef, 'createComponent');
        component.size = 'L';
        fixture.detectChanges();
        component.directive.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalledWith(SidebarCardLargeComponent);
    });
});
