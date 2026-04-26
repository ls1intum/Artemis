import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchNavigationViewComponent } from './global-search-navigation-view.component';
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
            it('should equal action button count plus searchable entities when not searching', () => {
                // actionButtonCount = 2 (iris + lecture both visible), searchableEntities.length = 5
                expect(component.itemCount()).toBe(7);
            });
        });

        describe('Keyboard navigation', () => {
            it('should emit SearchView.Iris when Enter is pressed at index 0', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(SearchView.Iris);
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

        it('itemCount should equal searchableEntities count when iris is disabled', () => {
            // actionButtonCount = 0 (iris disabled), searchableEntities.length = 5
            expect(component.itemCount()).toBe(5);
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
    });
});
