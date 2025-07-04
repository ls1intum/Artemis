import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarEventFilterComponent } from './calendar-event-filter.component';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';

describe('CalendarEventFilterComponent', () => {
    let component: CalendarEventFilterComponent;
    let fixture: ComponentFixture<CalendarEventFilterComponent>;
    let mockService: jest.Mocked<CalendarEventService>;

    const eventFilterOptions: CalendarEventFilterOption[] = ['lectureEvents', 'exerciseEvents', 'tutorialEvents', 'examEvents'];
    const includedEventFilterOptions = signal<CalendarEventFilterOption[]>(['lectureEvents', 'examEvents']);

    beforeEach(async () => {
        mockService = {
            toggleEventFilterOption: jest.fn(),
            eventFilterOptions: eventFilterOptions,
            includedEventFilterOptions: includedEventFilterOptions,
        } as unknown as jest.Mocked<CalendarEventService>;

        await TestBed.configureTestingModule({
            imports: [CalendarEventFilterComponent, NgbPopoverModule],
            declarations: [MockDirective(TranslateDirective)],
            providers: [{ provide: CalendarEventService, useValue: mockService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventFilterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should display chips only for included options', () => {
        const chips = fixture.debugElement.queryAll(By.css('.chip'));
        expect(chips).toHaveLength(2);

        const examChip = fixture.debugElement.query(By.css('[data-testid="chip-examEvents"]'));
        const lectureChip = fixture.debugElement.query(By.css('[data-testid="chip-lectureEvents"]'));

        expect(examChip).toBeTruthy();
        expect(lectureChip).toBeTruthy();
    });

    it('should call toggleOption when chip remove icon is clicked', () => {
        const chipRemoveButtons = fixture.debugElement.queryAll(By.css('.remove-btn'));
        chipRemoveButtons[0].nativeElement.click();
        fixture.detectChanges();

        expect(mockService.toggleEventFilterOption).toHaveBeenCalledWith('lectureEvents');
    });

    it('should show all filter options in the popover', () => {
        const filterButton = fixture.debugElement.query(By.css('button')).nativeElement;
        filterButton.click();
        fixture.detectChanges();

        const checkboxes = fixture.debugElement.queryAll(By.css('.dropdown-item'));
        expect(checkboxes).toHaveLength(4);

        const checkboxIds = checkboxes.map((checkbox) => checkbox.query(By.css('input')).attributes['id']);
        expect(checkboxIds).toEqual(['filter-lectureEvents', 'filter-exerciseEvents', 'filter-tutorialEvents', 'filter-examEvents']);
    });

    it('should check the box for included options and uncheck for excluded', () => {
        const filterButton = fixture.debugElement.query(By.css('button')).nativeElement;
        filterButton.click();
        fixture.detectChanges();

        const checkboxes = fixture.debugElement.queryAll(By.css('.dropdown-item'));

        checkboxes.forEach((item) => {
            const input = item.query(By.css('input')).nativeElement;
            const option = input.id.replace('filter-', '') as CalendarEventFilterOption;

            if (includedEventFilterOptions().includes(option)) {
                expect(input.checked).toBeTrue();
            } else {
                expect(input.checked).toBeFalse();
            }
        });
    });

    it('should call toggleOption when a checkbox is changed', () => {
        const filterButton = fixture.debugElement.query(By.css('button')).nativeElement;
        filterButton.click();
        fixture.detectChanges();

        const tutorialCheckbox = fixture.debugElement.queryAll(By.css('input')).find((input) => input.attributes['id'] === 'filter-tutorialEvents');

        tutorialCheckbox!.triggerEventHandler('change', {});
        fixture.detectChanges();

        expect(mockService.toggleEventFilterOption).toHaveBeenCalledWith('tutorialEvents');
    });

    it('should return correct CSS class for each option', () => {
        expect(component.getColorClassForFilteringOption('examEvents')).toBe('exam');
        expect(component.getColorClassForFilteringOption('lectureEvents')).toBe('lecture');
        expect(component.getColorClassForFilteringOption('tutorialEvents')).toBe('tutorial');
        expect(component.getColorClassForFilteringOption('exerciseEvents')).toBe('exercise');
    });

    it('should return correct translation key for each option', () => {
        expect(component.getFilterOptionString('examEvents')).toBe('artemisApp.calendar.examFilterOption');
        expect(component.getFilterOptionString('lectureEvents')).toBe('artemisApp.calendar.lectureFilterOption');
        expect(component.getFilterOptionString('tutorialEvents')).toBe('artemisApp.calendar.tutorialFilterOption');
        expect(component.getFilterOptionString('exerciseEvents')).toBe('artemisApp.calendar.exerciseFilterOption');
    });
});
