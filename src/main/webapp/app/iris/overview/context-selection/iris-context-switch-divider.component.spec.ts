import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IrisContextSwitchDividerComponent } from './iris-context-switch-divider.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisContextSwitchMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisJsonMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';

describe('IrisContextSwitchDividerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisContextSwitchDividerComponent;
    let fixture: ComponentFixture<IrisContextSwitchDividerComponent>;

    const courseId = 10;
    const chatServiceMock = { getCourseId: vi.fn().mockReturnValue(courseId) };

    function buildMessage(attributes: Record<string, unknown>): IrisContextSwitchMessage {
        const content = new IrisJsonMessageContent(attributes);
        return { id: 1, content: [content], sender: IrisSender.CTXSWAP };
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisContextSwitchDividerComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: IrisChatService, useValue: chatServiceMock }, { provide: TranslateService, useClass: MockTranslateService }, provideRouter([])],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisContextSwitchDividerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('contextSwitch computed signal', () => {
        it('reads transition, entityMode, entityId and name from JSON content', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'changed', entityMode: ChatServiceMode.LECTURE, entityId: 5, name: 'Week 1' }));
            await fixture.whenStable();

            const info = component.contextSwitch();
            expect(info.transition).toBe('changed');
            expect(info.entityIcon).toBe(faChalkboardUser);
            expect(info.entityRoute).toBe(`/courses/${courseId}/lectures/5`);
            expect(info.name).toBe('Week 1');
        });

        it('defaults transition to "added" when not present in JSON', async () => {
            fixture.componentRef.setInput('message', buildMessage({ entityMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 7, name: 'Ex 1' }));
            await fixture.whenStable();

            expect(component.contextSwitch().transition).toBe('added');
        });

        it('defaults name to empty string when not present in JSON', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'removed', entityMode: ChatServiceMode.LECTURE, entityId: 3 }));
            await fixture.whenStable();

            expect(component.contextSwitch().name).toBe('');
        });

        it('returns faKeyboard icon for PROGRAMMING_EXERCISE mode', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'added', entityMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 7, name: 'Prog Ex' }));
            await fixture.whenStable();

            expect(component.contextSwitch().entityIcon).toBe(faKeyboard);
        });

        it('returns faFont icon for TEXT_EXERCISE mode', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'added', entityMode: ChatServiceMode.TEXT_EXERCISE, entityId: 8, name: 'Text Ex' }));
            await fixture.whenStable();

            expect(component.contextSwitch().entityIcon).toBe(faFont);
        });

        it('returns exercise route for TEXT_EXERCISE mode', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'added', entityMode: ChatServiceMode.TEXT_EXERCISE, entityId: 8, name: 'Text Ex' }));
            await fixture.whenStable();

            expect(component.contextSwitch().entityRoute).toBe(`/courses/${courseId}/exercises/8`);
        });

        it('returns undefined entityRoute for COURSE mode (no entity route)', async () => {
            fixture.componentRef.setInput('message', buildMessage({ transition: 'removed', entityMode: ChatServiceMode.COURSE, entityId: courseId, name: 'My Course' }));
            await fixture.whenStable();

            expect(component.contextSwitch().entityRoute).toBeUndefined();
        });

        it('returns undefined entityIcon and entityRoute when message has no JSON content', async () => {
            const textOnly: IrisContextSwitchMessage = {
                id: 2,
                content: [new IrisTextMessageContent('plain text')],
                sender: IrisSender.CTXSWAP,
            };
            fixture.componentRef.setInput('message', textOnly);
            await fixture.whenStable();

            const info = component.contextSwitch();
            expect(info.entityIcon).toBeUndefined();
            expect(info.entityRoute).toBeUndefined();
            expect(info.transition).toBe('added');
            expect(info.name).toBe('');
        });
    });
});
