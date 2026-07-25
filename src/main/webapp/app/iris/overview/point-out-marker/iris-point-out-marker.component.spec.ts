import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
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
        return template.replace(/\{\{(\w+)}}/g, (_match, name) => String(params?.[name] ?? ''));
    };

    function buildMessage(...attributes: Record<string, unknown>[]): IrisCommandMessage {
        return { id: 1, content: attributes.map((attribute) => new IrisJsonMessageContent(attribute)), sender: IrisSender.COMMAND } as IrisCommandMessage;
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

    it('should build a labelled marker for a page point-out', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3, lectureUnitName: 'Sorting' }));

        expect(component.markers()).toHaveLength(1);
        expect(component.markers()[0].label).toBe('Navigated to page 3 in lecture unit Sorting');
    });

    it('should omit the lecture unit clause when the name is unknown', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3 }));

        expect(component.markers()[0].label).toBe('Navigated to page 3');
    });

    it('should join page and timestamp targets', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3, timestamp: 150 }));

        expect(component.markers()[0].label).toBe('Navigated to page 3 and timestamp 2:30');
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
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3, timestamp: 150 }));

        // The kit button keeps the real <button> internal, so drive the native element it renders.
        const buttons = fixture.nativeElement.querySelectorAll('tum-ui-button button');
        expect(buttons).toHaveLength(1);
        expect(buttons[0].textContent).toContain('Navigated to page 3 and timestamp 2:30');

        buttons[0].click();

        expect(chatServiceMock.navigateToPointOut).toHaveBeenCalledExactlyOnceWith({ type: 'pointOut', lectureUnitId: 42, page: 3, timestamp: 150, forceOpen: true });
    });

    it('should describe the button with a tooltip rather than overriding its accessible name', async () => {
        await setMessage(buildMessage({ type: 'pointOut', lectureUnitId: 42, page: 3 }));

        // The visible label is the accessible name; the hint is a tooltip, so no aria-label may shadow it.
        const button = fixture.nativeElement.querySelector('tum-ui-button button');
        expect(button.getAttribute('aria-label')).toBeNull();

        const tooltip = fixture.debugElement.query(By.directive(TumUiTooltipDirective));
        expect(tooltip.injector.get(TumUiTooltipDirective).content()).toBe('artemisApp.iris.pointOut.openTooltip');
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
