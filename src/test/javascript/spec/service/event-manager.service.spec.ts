import { TestBed } from '@angular/core/testing';
import { EventManager, EventWithContent } from 'app/core/util/event-manager.service';

describe('Event Manager tests', () => {
    describe('EventWithContent', () => {
        it('should create correctly EventWithContent', () => {
            // WHEN
            const eventWithContent = new EventWithContent('name', 'content');

            // THEN
            expect(eventWithContent).toEqual({ name: 'name', content: 'content' });
        });
    });

    describe('EventManager', () => {
        let receivedEvent: EventWithContent<unknown> | string | null;
        let eventManager: EventManager;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [EventManager],
            });
            receivedEvent = null;
            eventManager = TestBed.inject(EventManager);
        });

        it('should not fail when no subscriber and broadcasting', () => {
            expect(eventManager.observer).toBeUndefined();
            eventManager.broadcast({ name: 'modifier', content: 'modified something' });
        });

        it('should create an observable and callback when broadcasted EventWithContent', () => {
            // GIVEN
            eventManager.subscribe('modifier', (event: EventWithContent<unknown> | string) => (receivedEvent = event));

            // WHEN
            eventManager.broadcast({ name: 'unrelatedModifier', content: 'unrelated modification' });
            // THEN
            expect(receivedEvent).toBeNull();

            // WHEN
            eventManager.broadcast({ name: 'modifier', content: 'modified something' });
            // THEN
            expect(receivedEvent).toEqual({ name: 'modifier', content: 'modified something' });
        });

        it('should create an observable and callback when broadcasted string', () => {
            // GIVEN
            eventManager.subscribe('modifier', (event: EventWithContent<unknown> | string) => (receivedEvent = event));

            // WHEN
            eventManager.broadcast('unrelatedModifier');
            // THEN
            expect(receivedEvent).toBeNull();

            // WHEN
            eventManager.broadcast('modifier');
            // THEN
            expect(receivedEvent).toBe('modifier');
        });

        it('should subscribe to multiple events', () => {
            // GIVEN
            eventManager.subscribe(['modifier', 'modifier2'], (event: EventWithContent<unknown> | string) => (receivedEvent = event));

            // WHEN
            eventManager.broadcast('unrelatedModifier');
            // THEN
            expect(receivedEvent).toBeNull();

            // WHEN
            eventManager.broadcast({ name: 'modifier', content: 'modified something' });
            // THEN
            expect(receivedEvent).toEqual({ name: 'modifier', content: 'modified something' });

            // WHEN
            eventManager.broadcast('modifier2');
            // THEN
            expect(receivedEvent).toBe('modifier2');
        });
    });
});
