import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, Type, viewChild } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SidebarCardDirective } from 'app/course/sidebar/directive/sidebar-card.directive';
import { SidebarCardElement } from 'app/foundation/types/sidebar';
import { MockRouter } from 'test/helpers/mocks/mock-router';

/**
 * Extracts the component name from a createComponent spy.
 * We avoid importing SidebarCardSmallComponent, SidebarCardMediumComponent, and SidebarCardLargeComponent directly because their heavy dependency chains might
 * cause out-of-memory errors when running all tests on CI and slow down test execution.
 */
function getCreatedComponentName(spy: ReturnType<typeof vi.spyOn>): string {
    return (spy.mock.calls[0][0] as Type<unknown>).name;
}

@Component({
    template: ` <div jhiSidebarCard [size]="size" [itemSelected]="false" [sidebarItem]="sidebarItem" [groupKey]="groupKey"></div>`,
    imports: [SidebarCardDirective],
})
class TestHostComponent {
    directive = viewChild.required(SidebarCardDirective);
    size: string;
    sidebarItem?: SidebarCardElement;
    groupKey?: string;
}

describe('SidebarCardDirective', () => {
    setupTestBed({ zoneless: true });
    let component: TestHostComponent;
    let fixture: ComponentFixture<TestHostComponent>;
    const router = new MockRouter();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestHostComponent, SidebarCardDirective],
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
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        component = fixture.componentInstance;
        TestBed.inject(ActivatedRoute);
        fixture.changeDetectorRef.detectChanges();
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('directive and viewContainerRef should be defined', () => {
        expect(component.directive()).toBeDefined();
        expect(component.directive().viewContainerRef).toBeDefined();
    });

    it('should create SidebarCardSmallComponent when size is "S"', () => {
        const createComponentSpy = vi.spyOn(component.directive().viewContainerRef, 'createComponent');
        component.size = 'S';
        component.sidebarItem = { title: 'exercise-TestTitle', id: '1', size: 'S' };
        component.groupKey = 'exerciseChannels';

        fixture.changeDetectorRef.detectChanges();
        component.directive().ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardSmallComponent');
    });

    it('should create SidebarCardMediumComponent when size is "M"', () => {
        const createComponentSpy = vi.spyOn(component.directive().viewContainerRef, 'createComponent');
        component.size = 'M';
        component.sidebarItem = { title: 'exercise-TestTitle', id: '1', size: 'M' };
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        component.directive().ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardMediumComponent');
    });

    it('should create SidebarCardLargeComponent when size is "L"', () => {
        const createComponentSpy = vi.spyOn(component.directive().viewContainerRef, 'createComponent');
        component.size = 'L';
        component.sidebarItem = { title: 'exercise-TestTitle', id: '1', size: 'L' };
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        component.directive().ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardLargeComponent');
    });

    it('should remove the correct prefix from the name when groupKey is in channelTypes', () => {
        const prefixes = ['exercise-', 'lecture-', 'exam-'];
        const channelTypes = ['exerciseChannels', 'lectureChannels', 'examChannels'];

        for (let i = 0; i < prefixes.length; i++) {
            const prefix = prefixes[i];
            const groupKey = channelTypes[i];
            const nameWithPrefix = prefix + 'TestName';

            component.groupKey = groupKey;
            fixture.changeDetectorRef.detectChanges();
            const result = component.directive().removeChannelPrefix(nameWithPrefix);

            expect(result).toBe('TestName');
        }
    });

    it('should not remove the prefix if groupKey is not in channelTypes', () => {
        const nameWithPrefix = 'exercise-TestName';
        component.groupKey = 'otherGroup';
        fixture.changeDetectorRef.detectChanges();
        const result = component.directive().removeChannelPrefix(nameWithPrefix);

        expect(result).toBe(nameWithPrefix);
    });

    it('should not remove the prefix if name does not start with any of the prefixes', () => {
        const nameWithoutPrefix = 'TestName';
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        const result = component.directive().removeChannelPrefix(nameWithoutPrefix);

        expect(result).toBe(nameWithoutPrefix);
    });

    it('should handle empty name input', () => {
        const emptyName = '';
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        const result = component.directive().removeChannelPrefix(emptyName);

        expect(result).toBe('');
    });

    it('should handle undefined name input', () => {
        const undefinedName = undefined as unknown as string;
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        const result = component.directive().removeChannelPrefix(undefinedName);

        expect(result).toBe(undefinedName);
    });

    it('should handle null name input', () => {
        const nullName = null as unknown as string;
        component.groupKey = 'exerciseChannels';
        fixture.changeDetectorRef.detectChanges();
        const result = component.directive().removeChannelPrefix(nullName);

        expect(result).toBe(nullName);
    });
});
