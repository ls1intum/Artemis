import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslateService } from '@ngx-translate/core';
import { TumUiButtonComponent } from '@tumaet/ui-angular';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';

describe('ExerciseGroupDateNoticeComponent', () => {
    let fixture: ComponentFixture<ExerciseGroupDateNoticeComponent>;
    let component: ExerciseGroupDateNoticeComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseGroupDateNoticeComponent],
            providers: [provideTranslateService()],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupDateNoticeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should render the icon, message, and edit button in one inline flow', () => {
        const childTags = Array.from((fixture.nativeElement as HTMLElement).children).map((element) => element.tagName.toLowerCase());
        const button = fixture.debugElement.query(By.directive(TumUiButtonComponent)).componentInstance as TumUiButtonComponent;

        expect(childTags).toEqual(['fa-icon', 'span', 'tum-ui-button']);
        expect((fixture.nativeElement as HTMLElement).classList).toContain('d-block');
        expect((fixture.nativeElement as HTMLElement).dataset.testid).toBe('exercise-group-date-notice');
        expect(button.variant()).toBe('text');
        expect(button.severity()).toBe('primary');
        expect(button.size()).toBe('small');
    });

    it('should emit when the edit button is clicked', () => {
        const editGroupDatesSpy = vi.spyOn(component.editGroupDates, 'emit');
        const button = fixture.debugElement.query(By.css('tum-ui-button button')).nativeElement as HTMLButtonElement;

        button.click();

        expect(editGroupDatesSpy).toHaveBeenCalledOnce();
    });
});
