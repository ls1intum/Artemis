import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisPointOutMarkerComponent } from './iris-point-out-marker.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisCommandMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisJsonMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';

describe('IrisPointOutMarkerComponent', () => {
    let component: IrisPointOutMarkerComponent;
    let fixture: ComponentFixture<IrisPointOutMarkerComponent>;
    let translateService: TranslateService;

    const chatServiceMock = { navigateToPointOut: vi.fn() };

    /** Interpolates `{{param}}` placeholders so tests can assert on the composed label. */
    const interpolate = (key: string, params?: Record<string, unknown>): string => {
        const template =
            {
                'artemisApp.iris.pointOut.page': 'page {{page}}',
                'artemisApp.iris.pointOut.timestamp': 'timestamp {{time}}',
                'artemisApp.iris.pointOut.and': ' and ',
                'artemisApp.iris.pointOut.label': 'Navigated to {{target}} in lecture unit {{unit}}',
                'artemisApp.iris.pointOut.labelNoUnit': 'Navigated to {{target}}',
            }[key] ?? key;
        return template.replace(/\{\{(\w+)}}/g, (_match, name) => String(params![name]));
    };

    /**
     * Builds a COMMAND message from one marker per content entry. Each entry is given as a flat
     * `{ type, ...parameters }` object and stored in the persisted marker shape, `{ type, parameters }`.
     */
    function buildMessage(...markers: Record<string, unknown>[]): IrisCommandMessage {
        const content = markers.map(({ type, ...parameters }) => new IrisJsonMessageContent({ type, parameters }));
        return { id: 1, content, sender: IrisSender.COMMAND } as IrisCommandMessage;
    }

    async function setMessage(message: IrisCommandMessage): Promise<void> {
        fixture.componentRef.setInput('message', message);
        await fixture.whenStable();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisPointOutMarkerComponent],
            providers: [
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);
        vi.spyOn(translateService, 'instant').mockImplementation((key, params) => interpolate(key as string, params as Record<string, unknown>));

        fixture = TestBed.createComponent(IrisPointOutMarkerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should build a labelled marker for a page point-out, naming the lecture unit when it is known', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3, lectureUnitName: 'Sorting' }));

        expect(component.markers()).toHaveLength(1);
        expect(component.markers()[0].label).toBe('Navigated to page 3 in lecture unit Sorting');

        // Without a name there is nothing to name the unit by, so the clause is left out rather than left empty.
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3 }));

        expect(component.markers()[0].label).toBe('Navigated to page 3');
    });

    // The deck index is what the client navigates by, but the student reads the printed number off the slide and Iris
    // names it in its answer text — so the chip has to agree with those two, not with the index. An unnumbered slide
    // carries no printed number at all, and an unusable one is no better than none: the number is only a label, so it
    // is dropped rather than rejecting a point-out whose navigation is perfectly good. Iris names no page for such a
    // slide either, so nothing contradicts the deck index the label then falls back to.
    it.each([
        [5, 5],
        [undefined, 7],
        [0, 7],
        [-1, 7],
        [2.5, 7],
        ['5', 7],
        [null, 7],
    ])('should label a point-out on deck index 7 with %p as page %p', async (displayPage, expectedPage) => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 7, displayPage }));

        expect(component.markers()).toHaveLength(1);
        expect(component.markers()[0].label).toBe(`Navigated to page ${expectedPage}`);
        // Navigation is unaffected either way: clicking still goes to the deck index.
        expect(component.markers()[0].data.page).toBe(7);
    });

    it.each([
        [0, '0:00'],
        [59, '0:59'],
        [150, '2:30'],
        [3600, '1:00:00'],
        [3725, '1:02:05'],
    ])('should format a timestamp of %is as %s', async (seconds, expected) => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, timestamp: seconds }));

        expect(component.markers()[0].label).toBe(`Navigated to timestamp ${expected}`);
    });

    it('should ignore content that is not a valid point-out marker', async () => {
        const message = buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3 }, { type: 'somethingElse', lectureUnitId: 7 }, { type: 'pointOut', lectureUnitId: 7 });
        message.content.push(new IrisTextMessageContent('plain text'));
        await setMessage(message);

        // Only the first attribute set is a point-out with a page or timestamp.
        expect(component.markers()).toHaveLength(1);
        expect(component.markers()[0].data.lectureUnitId).toBe(42);
    });

    it('should render one kit button per marker and navigate with forceOpen on click', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, lectureId: 27, page: 3, displayPage: 9, timestamp: 150 }));

        const buttons = fixture.nativeElement.querySelectorAll('button[tumUiButton]');
        expect(buttons).toHaveLength(1);
        // Also the assertion that a marker naming both targets joins them into one label.
        expect(buttons[0].textContent).toContain('Navigated to page 9 and timestamp 2:30');
        // The visible label is the accessible name, so no aria-label may shadow it.
        expect(buttons[0].getAttribute('aria-label')).toBeNull();

        buttons[0].click();

        // The marker's own lecture travels with the click, as does the printed page number the chip is labelled with;
        // the chat's current context has no say in where the click leads.
        expect(chatServiceMock.navigateToPointOut).toHaveBeenCalledExactlyOnceWith({
            lectureUnitId: 42,
            lectureId: 27,
            page: 3,
            displayPage: 9,
            timestamp: 150,
            forceOpen: true,
        });
    });

    it('should rebuild labels when the language changes', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3 }));
        expect(component.markers()[0].label).toBe('Navigated to page 3');

        vi.spyOn(translateService, 'instant').mockImplementation((key, params) =>
            key === 'artemisApp.iris.pointOut.labelNoUnit' ? `DE ${params!['target']}` : interpolate(key as string, params as Record<string, unknown>),
        );
        translateService.use('de');
        await fixture.whenStable();

        expect(component.markers()[0].label).toBe('DE page 3');
    });
});
