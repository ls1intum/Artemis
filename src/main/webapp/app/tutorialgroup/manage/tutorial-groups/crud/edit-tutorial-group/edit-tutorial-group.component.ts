import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EffectRef,
    EnvironmentInjector,
    OnDestroy,
    OnInit,
    effect,
    inject,
    runInInjectionContext,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupFormComponent, TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { onError } from 'app/shared/util/global.utils';
import { Subject, combineLatest } from 'rxjs';
import { finalize, take, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResourceRef } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-edit-tutorial-group',
    templateUrl: './edit-tutorial-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFormComponent],
})
export class EditTutorialGroupComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private calendarService = inject(CalendarService);
    private cdr = inject(ChangeDetectorRef);
    private environmentInjector = inject(EnvironmentInjector);

    ngUnsubscribe = new Subject<void>();

    isLoading = false;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    formData: TutorialGroupFormData;
    tutorialGroupId: number;
    course: Course;
    private tutorialGroupsResource?: HttpResourceRef<Array<TutorialGroup> | undefined>;
    private loadEffect?: EffectRef;
    private lastTutorialGroups?: TutorialGroup[];

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: ([params, { course }]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.course = course;
                    this.tutorialGroupsResource = this.tutorialGroupService.getAllForCourseResource(this.course.id!);
                    this.setupTutorialGroupEffect();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    updateTutorialGroup(formData: TutorialGroupFormData) {
        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus, schedule, notificationText, updateTutorialGroupChannelName } = formData;
        const updatedTutorialGroup = new TutorialGroup();
        updatedTutorialGroup.id = this.tutorialGroup.id;
        updatedTutorialGroup.title = title;
        updatedTutorialGroup.teachingAssistant = teachingAssistant;
        updatedTutorialGroup.additionalInformation = additionalInformation;
        updatedTutorialGroup.capacity = capacity;
        updatedTutorialGroup.isOnline = isOnline;
        updatedTutorialGroup.language = language;
        updatedTutorialGroup.campus = campus;
        if (schedule) {
            updatedTutorialGroup.tutorialGroupSchedule = new TutorialGroupSchedule();
            if (this.tutorialGroup.tutorialGroupSchedule) {
                updatedTutorialGroup.tutorialGroupSchedule.id = this.tutorialGroup.tutorialGroupSchedule.id;
            }
            const { endTime, startTime, dayOfWeek, repetitionFrequency, period, location } = schedule;
            if (period && period.length === 2) {
                updatedTutorialGroup.tutorialGroupSchedule.validFromInclusive = dayjs(period[0]);
                updatedTutorialGroup.tutorialGroupSchedule.validToInclusive = dayjs(period[1]);
            }
            updatedTutorialGroup.tutorialGroupSchedule.dayOfWeek = dayOfWeek;
            updatedTutorialGroup.tutorialGroupSchedule.startTime = startTime;
            updatedTutorialGroup.tutorialGroupSchedule.endTime = endTime;
            updatedTutorialGroup.tutorialGroupSchedule.repetitionFrequency = repetitionFrequency;
            updatedTutorialGroup.tutorialGroupSchedule.location = location;
        } else {
            updatedTutorialGroup.tutorialGroupSchedule = undefined;
        }

        this.isLoading = true;
        this.tutorialGroupService
            .update(this.course.id!, this.tutorialGroupId, updatedTutorialGroup, notificationText, updateTutorialGroupChannelName)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.calendarService.reloadEvents();
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => this.onError(res),
            });
    }

    onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error && error.errorKey && error.errorKey === 'scheduleOverlapsWithSession') {
            this.alertService.error(error.message, error.params);
        } else {
            this.alertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.loadEffect?.destroy();
    }

    private setupTutorialGroupEffect() {
        if (!this.tutorialGroupsResource) {
            return;
        }
        this.loadEffect?.destroy();
        runInInjectionContext(this.environmentInjector, () => {
            this.loadEffect = effect(() => {
                const resource = this.tutorialGroupsResource;
                if (!resource) {
                    return;
                }
                this.isLoading = resource.isLoading();
                const error = resource.error();
                if (error) {
                    onError(this.alertService, error as HttpErrorResponse);
                }
                const tutorialGroups = resource.value();
                if (!tutorialGroups || tutorialGroups === this.lastTutorialGroups) {
                    return;
                }
                this.lastTutorialGroups = tutorialGroups;
                this.tutorialGroupService.convertTutorialGroupArrayDatesFromServer(tutorialGroups);
                const tutorialGroup = tutorialGroups.find((group) => group.id === this.tutorialGroupId);
                if (!tutorialGroup) {
                    return;
                }
                this.tutorialGroup = tutorialGroup;
                this.formData = {
                    title: this.tutorialGroup.title,
                    teachingAssistant: this.tutorialGroup.teachingAssistant,
                    additionalInformation: this.tutorialGroup.additionalInformation,
                    capacity: this.tutorialGroup.capacity,
                    isOnline: this.tutorialGroup.isOnline,
                    language: this.tutorialGroup.language,
                    campus: this.tutorialGroup.campus,
                };
                if (this.tutorialGroup.tutorialGroupSchedule) {
                    this.tutorialGroupSchedule = this.tutorialGroup.tutorialGroupSchedule;
                    this.formData.schedule = {
                        period: [this.tutorialGroupSchedule.validFromInclusive!.toDate(), this.tutorialGroupSchedule.validToInclusive!.toDate()],
                        repetitionFrequency: this.tutorialGroupSchedule.repetitionFrequency,
                        startTime: this.tutorialGroupSchedule.startTime,
                        endTime: this.tutorialGroupSchedule.endTime,
                        dayOfWeek: this.tutorialGroupSchedule.dayOfWeek,
                        location: this.tutorialGroupSchedule.location,
                    };
                }
                this.cdr.detectChanges();
            });
        });
    }
}
