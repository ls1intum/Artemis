import { TestBed } from '@angular/core/testing';
import { CourseSettingsComponent } from 'app/core/course/overview/course-settings/course-settings.component';
import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

@Component({
    selector: 'jhi-notification-settings',
    template: '<div>notification-settings-stub</div>',
    standalone: true,
})
class NotificationSettingsStubComponent {}

describe('CourseSettingsComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseSettingsComponent, NotificationSettingsStubComponent],
            providers: [{ provide: ActivatedRoute, useValue: { parent: { params: of({ courseId: 1 }) } } }],
        }).compileComponents();

        TestBed.overrideComponent(CourseSettingsComponent, {
            set: {
                imports: [NotificationSettingsStubComponent],
            },
        });
    });

    it('should render notification settings', () => {
        const fixture = TestBed.createComponent(CourseSettingsComponent);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('notification-settings-stub');
    });
});
