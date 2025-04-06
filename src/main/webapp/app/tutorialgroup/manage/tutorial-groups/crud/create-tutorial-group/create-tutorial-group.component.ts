import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { finalize, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject } from 'rxjs';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupFormComponent, TutorialGroupFormData } from '../tutorial-group-form/tutorial-group-form.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/services/tutorial-groups.service';

@Component({
    selector: 'jhi-create-tutorial-group',
    templateUrl: './create-tutorial-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFormComponent],
})
export class CreateTutorialGroupComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);

    tutorialGroupToCreate: TutorialGroup = new TutorialGroup();
    isLoading: boolean;
    course: Course;

    ngUnsubscribe = new Subject<void>();

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntil(this.ngUnsubscribe)).subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
        });
        this.tutorialGroupToCreate = new TutorialGroup();
    }

    createTutorialGroup(formData: TutorialGroupFormData) {
        const { title, teachingAssistant, additionalInformation, capacity, isOnline, language, campus, schedule } = formData;
        this.tutorialGroupToCreate.title = title;
        this.tutorialGroupToCreate.teachingAssistant = teachingAssistant;
        this.tutorialGroupToCreate.additionalInformation = additionalInformation;
        this.tutorialGroupToCreate.capacity = capacity;
        this.tutorialGroupToCreate.isOnline = isOnline;
        this.tutorialGroupToCreate.language = language;
        this.tutorialGroupToCreate.campus = campus;

        if (schedule) {
            this.tutorialGroupToCreate.tutorialGroupSchedule = new TutorialGroupSchedule();
            if (schedule.period && schedule.period.length === 2) {
                this.tutorialGroupToCreate.tutorialGroupSchedule.validFromInclusive = dayjs(schedule.period[0]);
                this.tutorialGroupToCreate.tutorialGroupSchedule.validToInclusive = dayjs(schedule.period[1]);
            }
            this.tutorialGroupToCreate.tutorialGroupSchedule.dayOfWeek = schedule.dayOfWeek;
            this.tutorialGroupToCreate.tutorialGroupSchedule.startTime = schedule.startTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.endTime = schedule.endTime;
            this.tutorialGroupToCreate.tutorialGroupSchedule.repetitionFrequency = schedule.repetitionFrequency;
            this.tutorialGroupToCreate.tutorialGroupSchedule.location = schedule.location;
        }

        this.isLoading = true;

        this.tutorialGroupService
            .create(this.tutorialGroupToCreate, this.course.id!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
