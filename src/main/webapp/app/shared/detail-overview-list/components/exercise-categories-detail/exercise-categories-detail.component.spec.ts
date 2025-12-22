import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgStyle } from '@angular/common';
import { ExerciseCategoriesDetailComponent } from 'app/shared/detail-overview-list/components/exercise-categories-detail/exercise-categories-detail.component';
import { ExerciseCategoriesDetail } from 'app/shared/detail-overview-list/detail.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';
import { MockComponent } from 'ng-mocks';

describe('ExerciseCategoriesDetailComponent', () => {
    let component: ExerciseCategoriesDetailComponent;
    let fixture: ComponentFixture<ExerciseCategoriesDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseCategoriesDetailComponent],
        })
            .overrideComponent(ExerciseCategoriesDetailComponent, {
                set: {
                    imports: [NgStyle, MockComponent(NoDataComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseCategoriesDetailComponent);
        component = fixture.componentInstance;
    });

    it('should display category badges when valid categories exist', () => {
        const categories: ExerciseCategory[] = [new ExerciseCategory('Test Category', '#ff0000'), new ExerciseCategory('Another Category', '#00ff00')];

        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        expect(component.hasValidCategories()).toBeTrue();

        const badges = fixture.debugElement.queryAll(By.css('.category-badge'));
        expect(badges).toHaveLength(2);
        expect(badges[0].nativeElement.textContent.trim()).toBe('Test Category');
        expect(badges[1].nativeElement.textContent.trim()).toBe('Another Category');
    });

    it('should apply correct background colors to category badges', () => {
        const categories: ExerciseCategory[] = [new ExerciseCategory('Red Category', '#ff0000'), new ExerciseCategory('Blue Category', '#0000ff')];

        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        const badges = fixture.debugElement.queryAll(By.css('.category-badge'));
        expect(badges[0].styles['backgroundColor']).toBe('rgb(255, 0, 0)');
        expect(badges[1].styles['backgroundColor']).toBe('rgb(0, 0, 255)');
    });

    it('should show no-data component when categories array is empty', () => {
        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories: [] },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        expect(component.hasValidCategories()).toBeFalse();

        const noDataComponent = fixture.debugElement.query(By.directive(NoDataComponent));
        expect(noDataComponent).toBeTruthy();

        const badges = fixture.debugElement.queryAll(By.css('.category-badge'));
        expect(badges).toHaveLength(0);
    });

    it('should show no-data component when categories is undefined', () => {
        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories: undefined },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        expect(component.hasValidCategories()).toBeFalse();

        const noDataComponent = fixture.debugElement.query(By.directive(NoDataComponent));
        expect(noDataComponent).toBeTruthy();
    });

    it('should show no-data component when categories have no valid category names', () => {
        const categories: ExerciseCategory[] = [new ExerciseCategory('', '#ff0000'), new ExerciseCategory(undefined, '#00ff00')];

        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        expect(component.hasValidCategories()).toBeFalse();

        const noDataComponent = fixture.debugElement.query(By.directive(NoDataComponent));
        expect(noDataComponent).toBeTruthy();
    });

    it('should only display categories with valid names', () => {
        const categories: ExerciseCategory[] = [
            new ExerciseCategory('Valid Category', '#ff0000'),
            new ExerciseCategory('', '#00ff00'),
            new ExerciseCategory('Another Valid', '#0000ff'),
        ];

        const detail: ExerciseCategoriesDetail = {
            type: DetailType.ExerciseCategories,
            data: { categories },
        };

        fixture.componentRef.setInput('detail', detail);
        fixture.detectChanges();

        expect(component.hasValidCategories()).toBeTrue();

        const badges = fixture.debugElement.queryAll(By.css('.category-badge'));
        expect(badges).toHaveLength(2);
        expect(badges[0].nativeElement.textContent.trim()).toBe('Valid Category');
        expect(badges[1].nativeElement.textContent.trim()).toBe('Another Valid');
    });
});
