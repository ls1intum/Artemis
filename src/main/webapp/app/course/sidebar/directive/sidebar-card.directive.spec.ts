import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { Type, WritableSignal, inputBinding, signal } from '@angular/core';
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

describe('SidebarCardDirective', () => {
    let fixture: DirectiveFixture<SidebarCardDirective>;
    let size: WritableSignal<string>;
    let sidebarItem: WritableSignal<SidebarCardElement | undefined>;
    let groupKey: WritableSignal<string | undefined>;
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
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
        });

        size = signal('');
        sidebarItem = signal<SidebarCardElement | undefined>(undefined);
        groupKey = signal<string | undefined>(undefined);
        fixture = TestBed.createDirective(SidebarCardDirective, {
            tagName: 'div',
            bindings: [inputBinding('size', size), inputBinding('itemSelected', () => false), inputBinding('sidebarItem', sidebarItem), inputBinding('groupKey', groupKey)],
        });
        TestBed.inject(ActivatedRoute);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('directive and viewContainerRef should be defined', () => {
        expect(fixture.directiveInstance).toBeDefined();
        expect(fixture.directiveInstance.viewContainerRef).toBeDefined();
    });

    it('should create SidebarCardSmallComponent when size is "S"', () => {
        const createComponentSpy = vi.spyOn(fixture.directiveInstance.viewContainerRef, 'createComponent');
        size.set('S');
        sidebarItem.set({ title: 'exercise-TestTitle', id: '1', size: 'S' });
        groupKey.set('exerciseChannels');

        fixture.detectChanges();
        fixture.directiveInstance.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardSmallComponent');
    });

    it('should create SidebarCardMediumComponent when size is "M"', () => {
        const createComponentSpy = vi.spyOn(fixture.directiveInstance.viewContainerRef, 'createComponent');
        size.set('M');
        sidebarItem.set({ title: 'exercise-TestTitle', id: '1', size: 'M' });
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        fixture.directiveInstance.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardMediumComponent');
    });

    it('should create SidebarCardLargeComponent when size is "L"', () => {
        const createComponentSpy = vi.spyOn(fixture.directiveInstance.viewContainerRef, 'createComponent');
        size.set('L');
        sidebarItem.set({ title: 'exercise-TestTitle', id: '1', size: 'L' });
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        fixture.directiveInstance.ngOnInit();

        expect(createComponentSpy).toHaveBeenCalled();
        expect(getCreatedComponentName(createComponentSpy)).toBe('SidebarCardLargeComponent');
    });

    it('should remove the correct prefix from the name when groupKey is in channelTypes', () => {
        const prefixes = ['exercise-', 'lecture-', 'exam-'];
        const channelTypes = ['exerciseChannels', 'lectureChannels', 'examChannels'];

        for (let i = 0; i < prefixes.length; i++) {
            const prefix = prefixes[i];
            const channelType = channelTypes[i];
            const nameWithPrefix = prefix + 'TestName';

            groupKey.set(channelType);
            fixture.detectChanges();
            const result = fixture.directiveInstance.removeChannelPrefix(nameWithPrefix);

            expect(result).toBe('TestName');
        }
    });

    it('should not remove the prefix if groupKey is not in channelTypes', () => {
        const nameWithPrefix = 'exercise-TestName';
        groupKey.set('otherGroup');
        fixture.detectChanges();
        const result = fixture.directiveInstance.removeChannelPrefix(nameWithPrefix);

        expect(result).toBe(nameWithPrefix);
    });

    it('should not remove the prefix if name does not start with any of the prefixes', () => {
        const nameWithoutPrefix = 'TestName';
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        const result = fixture.directiveInstance.removeChannelPrefix(nameWithoutPrefix);

        expect(result).toBe(nameWithoutPrefix);
    });

    it('should handle empty name input', () => {
        const emptyName = '';
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        const result = fixture.directiveInstance.removeChannelPrefix(emptyName);

        expect(result).toBe('');
    });

    it('should handle undefined name input', () => {
        const undefinedName = undefined as unknown as string;
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        const result = fixture.directiveInstance.removeChannelPrefix(undefinedName);

        expect(result).toBe(undefinedName);
    });

    it('should handle null name input', () => {
        const nullName = null as unknown as string;
        groupKey.set('exerciseChannels');
        fixture.detectChanges();
        const result = fixture.directiveInstance.removeChannelPrefix(nullName);

        expect(result).toBe(nullName);
    });
});
