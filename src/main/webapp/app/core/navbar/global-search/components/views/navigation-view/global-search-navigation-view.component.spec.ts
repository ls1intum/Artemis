import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchNavigationViewComponent, NAV_ACTION_COUNT } from './global-search-navigation-view.component';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('GlobalSearchNavigationViewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchNavigationViewComponent;
    let fixture: ComponentFixture<GlobalSearchNavigationViewComponent>;

    // jsdom does not implement scrollIntoView; mock it to prevent TypeError in the effect
    HTMLElement.prototype.scrollIntoView = vi.fn();

    function configureTestBed(irisEnabled: boolean): void {
        TestBed.configureTestingModule({
            imports: [GlobalSearchNavigationViewComponent, MockComponent(GlobalSearchActionItemComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(irisEnabled) } },
            ],
        });

        fixture = TestBed.createComponent(GlobalSearchNavigationViewComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('searchQuery', '');
        fixture.componentRef.setInput('selectedIndex', -1);
        fixture.detectChanges();
    }

    describe('when iris is enabled', () => {
        beforeEach(() => {
            vi.clearAllMocks();
            configureTestBed(true);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        describe('itemCount', () => {
            it('should equal NAV_ACTION_COUNT (1) with no nav results', () => {
                expect(component.itemCount()).toBe(NAV_ACTION_COUNT);
                expect(component.itemCount()).toBe(1);
            });
        });

        describe('Keyboard navigation', () => {
            it('should emit SearchView.Lecture when Enter is pressed at index 0', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(SearchView.Lecture);
            });

            it('should call preventDefault when Enter is pressed at index 0', () => {
                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

                component.handleKeydown(event);

                expect(preventDefaultSpy).toHaveBeenCalled();
            });

            it('should not emit when Enter is pressed at index -1', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', -1);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).not.toHaveBeenCalled();
            });

            it('should not emit for non-Enter keys', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
                component.handleKeydown(event);

                expect(spy).not.toHaveBeenCalled();
            });
        });

        describe('resultSelectedIndex', () => {
            it('should subtract NAV_ACTION_COUNT from selectedIndex', () => {
                fixture.componentRef.setInput('selectedIndex', 3);
                fixture.detectChanges();

                expect((component as any).resultSelectedIndex()).toBe(3 - NAV_ACTION_COUNT);
            });
        });

        describe('template', () => {
            it('should render the lecture content action button', () => {
                const button = fixture.nativeElement.querySelector('jhi-global-search-action-item');
                expect(button).toBeTruthy();
            });
        });
    });

    describe('when iris is disabled', () => {
        beforeEach(() => {
            vi.clearAllMocks();
            configureTestBed(false);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('itemCount should be 0 with no nav results', () => {
            expect(component.itemCount()).toBe(0);
        });

        it('should not emit when Enter is pressed at index 0', () => {
            const spy = vi.fn();
            component.viewSelected.subscribe(spy);

            fixture.componentRef.setInput('selectedIndex', 0);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            component.handleKeydown(event);

            expect(spy).not.toHaveBeenCalled();
        });

        it('should not render the lecture content action button', () => {
            const button = fixture.nativeElement.querySelector('jhi-global-search-action-item');
            expect(button).toBeNull();
        });

        describe('resultSelectedIndex', () => {
            it('should return selectedIndex unchanged when iris is disabled', () => {
                fixture.componentRef.setInput('selectedIndex', 3);
                fixture.detectChanges();

                // When iris is disabled the offset is 0, so resultSelectedIndex = selectedIndex
                expect((component as any).resultSelectedIndex()).toBe(3);
            });
        });
    });
});
