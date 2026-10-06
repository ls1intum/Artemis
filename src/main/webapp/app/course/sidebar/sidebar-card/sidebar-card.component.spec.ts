import { beforeEach, describe, expect, it } from 'vitest';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { SidebarCardComponent } from 'app/course/sidebar/sidebar-card/sidebar-card.component';
import { SidebarCardElement, SidebarCardSize, SidebarTypes } from 'app/foundation/types/sidebar';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';

// Stand-ins for the three cards with the inputs and outputs the real ones declare. The real cards pull in router links, the
// conversation options and the build trigger button, none of which this wrapper needs to be tested.
@Component({ selector: 'jhi-small-sidebar-card', template: '' })
class StubSmallCardComponent {
    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly groupKey = input<string>();
    readonly onUpdateSidebar = output<void>();
}

@Component({ selector: 'jhi-medium-sidebar-card', template: '' })
class StubMediumCardComponent {
    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly groupKey = input<string>();
    readonly activeItemId = input<number>();
}

@Component({ selector: 'jhi-large-sidebar-card', template: '' })
class StubLargeCardComponent {
    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly groupKey = input<string>();
}

describe('SidebarCardComponent', () => {
    let fixture: ComponentFixture<SidebarCardComponent>;

    const item = (size: SidebarCardSize, title = 'exercise-Homework', conversation?: ConversationDTO): SidebarCardElement => ({ title, id: '7', size, conversation });
    const render = (sidebarItem: SidebarCardElement | undefined, size: SidebarCardSize = 'M') => {
        fixture.componentRef.setInput('size', size);
        fixture.componentRef.setInput('sidebarItem', sidebarItem);
        fixture.detectChanges();
    };
    const small = () => fixture.debugElement.query(By.directive(StubSmallCardComponent))?.componentInstance as StubSmallCardComponent | undefined;
    const medium = () => fixture.debugElement.query(By.directive(StubMediumCardComponent))?.componentInstance as StubMediumCardComponent | undefined;
    const large = () => fixture.debugElement.query(By.directive(StubLargeCardComponent))?.componentInstance as StubLargeCardComponent | undefined;

    beforeEach(() => {
        TestBed.overrideComponent(SidebarCardComponent, { set: { imports: [StubSmallCardComponent, StubMediumCardComponent, StubLargeCardComponent] } });
        fixture = TestBed.createComponent(SidebarCardComponent);
    });

    describe('card by size', () => {
        it.each([
            ['S', small, [medium, large]],
            ['M', medium, [small, large]],
            ['L', large, [small, medium]],
        ] as const)('should render only the card of size %s', (size, shown, hidden) => {
            render(item(size), size);

            expect(shown()).toBeDefined();
            hidden.forEach((other) => expect(other()).toBeUndefined());
        });

        it('should swap the card when the size changes', () => {
            render(item('S'), 'S');
            expect(small()).toBeDefined();

            fixture.componentRef.setInput('size', 'L');
            fixture.detectChanges();

            expect(small()).toBeUndefined();
            expect(large()).toBeDefined();
        });

        it('should render no card without an item', () => {
            render(undefined, 'S');

            expect(small()).toBeUndefined();
            expect(medium()).toBeUndefined();
            expect(large()).toBeUndefined();
        });

        it('should render no card for an unknown size', () => {
            render(item('S'), 'X' as SidebarCardSize);

            expect(small()).toBeUndefined();
            expect(medium()).toBeUndefined();
            expect(large()).toBeUndefined();
        });
    });

    describe('inputs passed on to the card', () => {
        it('should pass the shared inputs to every card', () => {
            fixture.componentRef.setInput('sidebarType', 'conversation');
            fixture.componentRef.setInput('itemSelected', true);
            fixture.componentRef.setInput('groupKey', 'general');

            const cards = [
                ['S', small],
                ['M', medium],
                ['L', large],
            ] as const;
            cards.forEach(([size, card]) => {
                render(item(size), size);

                expect(card()?.sidebarType()).toBe('conversation');
                expect(card()?.itemSelected()).toBe(true);
                expect(card()?.groupKey()).toBe('general');
            });
        });

        it('should pass the active item id to the medium card', () => {
            fixture.componentRef.setInput('activeItemId', 42);

            render(item('M'), 'M');

            expect(medium()?.activeItemId()).toBe(42);
        });

        it('should report that the sidebar has to be reloaded when the small card does', () => {
            let reloads = 0;
            fixture.componentInstance.onUpdateSidebar.subscribe(() => reloads++);
            render(item('S'), 'S');

            small()!.onUpdateSidebar.emit();

            expect(reloads).toBe(1);
        });
    });

    describe('item handed to the card', () => {
        it.each([
            ['exerciseChannels', 'exercise-Homework', 'Homework'],
            ['lectureChannels', 'lecture-Intro', 'Intro'],
            ['examChannels', 'exam-Final', 'Final'],
        ])('should remove the channel prefix of the %s group', (groupKey, title, expected) => {
            fixture.componentRef.setInput('groupKey', groupKey);

            render(item('S', title), 'S');

            expect(small()?.sidebarItem().title).toBe(expected);
        });

        it('should keep the title outside of the channel groups', () => {
            fixture.componentRef.setInput('groupKey', 'general');

            render(item('S', 'exercise-Homework'), 'S');

            expect(small()?.sidebarItem().title).toBe('exercise-Homework');
        });

        it('should keep a title that has no known prefix in a channel group', () => {
            fixture.componentRef.setInput('groupKey', 'exerciseChannels');

            render(item('S', 'Homework'), 'S');

            expect(small()?.sidebarItem().title).toBe('Homework');
        });

        it('should keep the live conversation so that changes to it reach the card, and leave the original item alone', () => {
            const conversation = { id: 3, type: 'channel', unreadMessagesCount: 2 } as ConversationDTO;
            const original = item('S', 'exercise-Homework', conversation);
            fixture.componentRef.setInput('groupKey', 'exerciseChannels');

            render(original, 'S');

            const cardItem = small()!.sidebarItem();
            expect(cardItem.title).toBe('Homework');
            expect(cardItem.conversation).toBe(conversation);
            expect(cardItem).not.toBe(original);
            expect(original.title).toBe('exercise-Homework');
        });

        it('should not add a conversation to an item that has none', () => {
            render({ title: 'exercise-Homework', id: '7', size: 'S' }, 'S');

            expect(Object.keys(small()!.sidebarItem())).not.toContain('conversation');
        });
    });
});
