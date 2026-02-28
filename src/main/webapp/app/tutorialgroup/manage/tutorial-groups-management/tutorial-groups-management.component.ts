import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, combineLatest, finalize } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { faPlus, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { takeUntil } from 'rxjs/operators';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupsImportButtonComponent } from './tutorial-groups-import-button/tutorial-groups-import-button.component';
import { TutorialGroupsExportButtonComponent } from './tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { TutorialGroupsCourseInformationComponent } from './tutorial-groups-course-information/tutorial-groups-course-information.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorialGroupsTableComponent } from 'app/tutorialgroup/manage/tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroupFreeDaysOverviewComponent } from 'app/tutorialgroup/shared/tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        LoadingIndicatorContainerComponent,
        NgbTooltip,
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownItem,
        TutorialGroupsImportButtonComponent,
        TutorialGroupsExportButtonComponent,
        TutorialGroupsTableComponent,
        TutorialGroupRowButtonsComponent,
        TutorialGroupsCourseInformationComponent,
        TutorialGroupFreeDaysOverviewComponent,
        ArtemisTranslatePipe,
    ],
})
export class TutorialGroupsManagementComponent implements OnInit, OnDestroy {
    private tutorialGroupService = inject(TutorialGroupsService);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    courseId: number;
    course: Course;
    isAtLeastInstructor = false;

    configuration: TutorialGroupsConfiguration;

    isLoading = false;
    tutorialGroups: TutorialGroup[] = [];
    faPlus = faPlus;
    faUmbrellaBeach = faUmbrellaBeach;

    readonly isMessagingEnabled = isMessagingEnabled;

    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntil(this.ngUnsubscribe)).subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.courseId = course.id!;
                this.isAtLeastInstructor = course.isAtLeastInstructor;
                this.loadTutorialGroups();
            }
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    loadTutorialGroups() {
        this.isLoading = true;

        combineLatest([this.tutorialGroupService.getAllForCourse(this.courseId), this.tutorialGroupsConfigurationService.getOneOfCourse(this.course.id!)])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: ([tutorialGroupsRes, configurationRes]) => {
                    const tutorialGroups = tutorialGroupsRes.body!;
                    tutorialGroups.sort((a, b) => {
                        if (a.isUserTutor && !b.isUserTutor) {
                            return -1;
                        } else if (!a.isUserTutor && b.isUserTutor) {
                            return 1;
                        } else {
                            return a.title!.localeCompare(b.title!);
                        }
                    });
                    this.tutorialGroups = tutorialGroups;

                    this.configuration = configurationRes.body!;
                    if (this.configuration.tutorialGroupFreePeriods) {
                        this.tutorialGroupFreeDays = this.configuration.tutorialGroupFreePeriods;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }
}
