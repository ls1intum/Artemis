import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchNavigationViewComponent } from './global-search-navigation-view.component';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { GlobalSearchResult } from 'app/core/navbar/global-search/services/global-search.service';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram, faQuestion } from '@fortawesome/free-solid-svg-icons';

describe('GlobalSearchNavigationViewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchNavigationViewComponent;
    let fixture: ComponentFixture<GlobalSearchNavigationViewComponent>;

    // jsdom does not implement scrollIntoView; mock it to prevent TypeError in the effect
    HTMLElement.prototype.scrollIntoView = vi.fn();

    const mockOverlayService = {
        close: vi.fn(),
    };

    function configureTestBed(irisEnabled: boolean): void {
        TestBed.configureTestingModule({
            imports: [GlobalSearchNavigationViewComponent, MockComponent(GlobalSearchActionItemComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(irisEnabled) } },
                { provide: SearchOverlayService, useValue: mockOverlayService },
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
                // actionButtonCount = 2 (iris + lecture both visible), searchableEntities.length = 7
                expect(component.itemCount()).toBe(9);
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

        describe('Lecture button keyboard navigation', () => {
            it('should emit SearchView.Lecture when Enter is pressed at index 1', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 1);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(SearchView.Lecture);
            });
        });

        describe('getIconForType', () => {
            it('should return faKeyboard for Programming exercises', () => {
                expect((component as any).getIconForType('exercise', 'Programming')).toBe(faKeyboard);
            });

            it('should return faProjectDiagram for Modeling exercises', () => {
                expect((component as any).getIconForType('exercise', 'Modeling')).toBe(faProjectDiagram);
            });

            it('should return faFont for Text exercises', () => {
                expect((component as any).getIconForType('exercise', 'Text')).toBe(faFont);
            });

            it('should return faFileUpload for File Upload exercises', () => {
                expect((component as any).getIconForType('exercise', 'File Upload')).toBe(faFileUpload);
            });

            it('should return faCheckDouble for Quiz exercises', () => {
                expect((component as any).getIconForType('exercise', 'Quiz')).toBe(faCheckDouble);
            });

            it('should return faQuestion for unknown exercise badge', () => {
                expect((component as any).getIconForType('exercise', 'Unknown')).toBe(faQuestion);
            });

            it('should return faQuestion for non-exercise type', () => {
                expect((component as any).getIconForType('lecture')).toBe(faQuestion);
            });
        });

        describe('navigateToResult', () => {
            it('should navigate to exercise URL and close overlay', () => {
                const router = TestBed.inject(Router);
                const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

                const result: GlobalSearchResult = { id: '42', type: 'exercise', title: 'Test', badge: 'Programming', metadata: { courseId: '10' } };
                (component as any).navigateToResult(result);

                expect(navigateSpy).toHaveBeenCalledWith(['/courses', '10', 'exercises', '42']);
                expect(mockOverlayService.close).toHaveBeenCalled();
            });

            it('should close overlay even when result has no courseId', () => {
                const router = TestBed.inject(Router);
                const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

                const result: GlobalSearchResult = { id: '42', type: 'exercise', title: 'Test', badge: 'Programming', metadata: {} };
                (component as any).navigateToResult(result);

                expect(navigateSpy).not.toHaveBeenCalled();
                expect(mockOverlayService.close).toHaveBeenCalled();
            });
        });

        describe('onEntityItemClick', () => {
            it('should emit entityClick event', () => {
                const spy = vi.fn();
                component.entityClick.subscribe(spy);

                const entity = { id: 'exercises', title: 'Exercises', description: 'desc', icon: faQuestion, type: 'page' as const, enabled: true };
                (component as any).onEntityItemClick(entity);

                expect(spy).toHaveBeenCalledWith(entity);
            });
        });

        describe('itemCount with results', () => {
            it('should count action buttons plus results when showing results', () => {
                fixture.componentRef.setInput('showResults', true);
                fixture.componentRef.setInput('results', [
                    { id: '1', type: 'exercise', title: 'Ex1', badge: 'Programming', metadata: {} },
                    { id: '2', type: 'exercise', title: 'Ex2', badge: 'Quiz', metadata: {} },
                ]);
                fixture.detectChanges();

                // actionButtonCount = 2 (iris + lecture), results = 2
                expect(component.itemCount()).toBe(4);
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
            // actionButtonCount = 0 (iris disabled), searchableEntities.length = 7
            expect(component.itemCount()).toBe(7);
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
