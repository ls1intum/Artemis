import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { debounceTime, filter, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentUnit, IngestionState } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { IconDefinition, faCheckCircle, faFileExport, faPencilAlt, faRepeat, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { PROFILE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styleUrls: ['./lecture-unit-management.component.scss'],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    @Input() showCreationCard = true;
    @Input() showCompetencies = true;
    @Input() emitEditEvents = false;

    @Input() lectureId: number | undefined;

    @Output()
    onEditLectureUnitClicked: EventEmitter<LectureUnit> = new EventEmitter<LectureUnit>();

    lectureUnits: LectureUnit[] = [];
    lecture: Lecture;
    isLoading = false;
    updateOrderSubject: Subject<any>;
    updateOrderSubjectSubscription: Subscription;
    navigationEndSubscription: Subscription;
    readonly LectureUnitType = LectureUnitType;
    readonly ActionType = ActionType;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    private profileInfoSubscription: Subscription;
    irisEnabled = false;
    lectureIngestionEnabled = false;

    routerEditLinksBase = {
        [LectureUnitType.ATTACHMENT]: 'attachment-units',
        [LectureUnitType.VIDEO]: 'video-units',
        [LectureUnitType.TEXT]: 'text-units',
        [LectureUnitType.ONLINE]: 'online-units',
    };

    // Icons
    faTrash = faTrash;
    faPencilAlt = faPencilAlt;
    sendToIris = faFileExport;
    resendToIris = faRepeat;
    done = faCheckCircle;
    loading = faSpinner;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private lectureService: LectureService,
        private alertService: AlertService,
        public lectureUnitService: LectureUnitService,
        private profileService: ProfileService,
        private irisSettingsService: IrisSettingsService,
    ) {}

    ngOnDestroy(): void {
        this.updateOrder();
        this.updateOrderSubjectSubscription.unsubscribe();
        this.dialogErrorSource.unsubscribe();
        this.navigationEndSubscription.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
    }

    ngOnInit(): void {
        this.navigationEndSubscription = this.router.events.pipe(filter((value) => value instanceof NavigationEnd)).subscribe(() => {
            this.loadData();
        });

        this.updateOrderSubject = new Subject();
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.lectureId ??= +params['lectureId'];
            if (this.lectureId) {
                // TODO: the lecture (without units) is already available through the lecture.route.ts resolver, it's not really good that we load it twice
                this.loadData();
            }
            this.initializeProfileInfo();
        });

        // debounceTime limits the amount of put requests sent for updating the lecture unit order
        this.updateOrderSubjectSubscription = this.updateOrderSubject.pipe(debounceTime(1000)).subscribe(() => {
            this.updateOrder();
        });
    }

    loadData() {
        this.isLoading = true;
        // TODO: we actually would like to have the lecture with all units! Posts and competencies are not required here
        // we could also simply load all units for the lecture (as the lecture is already available through the route, see TODO above)
        this.lectureService
            .findWithDetails(this.lectureId!)
            .pipe(
                map((response: HttpResponse<Lecture>) => response.body!),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (lecture) => {
                    this.lecture = lecture;
                    if (lecture?.lectureUnits) {
                        this.lectureUnits = lecture?.lectureUnits;
                    } else {
                        this.lectureUnits = [];
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    updateOrder() {
        if (this.lectureId === undefined || isNaN(this.lectureId)) {
            return;
        }

        this.lectureUnitService
            .updateOrder(this.lectureId!, this.lectureUnits)
            .pipe(map((response: HttpResponse<LectureUnit[]>) => response.body!))
            .subscribe({
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    initializeProfileInfo() {
        this.profileInfoSubscription = this.profileService.getProfileInfo().subscribe(async (profileInfo) => {
            this.irisEnabled = profileInfo.activeProfiles.includes(PROFILE_IRIS);
            if (this.irisEnabled && this.lecture.course && this.lecture.course.id) {
                this.irisSettingsService.getCombinedCourseSettings(this.lecture.course.id).subscribe((settings) => {
                    this.lectureIngestionEnabled = settings?.irisLectureIngestionSettings?.enabled || false;
                });
            }
        });
    }

    drop(event: CdkDragDrop<LectureUnit[]>) {
        moveItemInArray(this.lectureUnits, event.previousIndex, event.currentIndex);
        this.updateOrderSubject.next('');
    }

    identify(index: number, lectureUnit: LectureUnit) {
        return `${index}-${lectureUnit.id}`;
    }

    getDeleteQuestionKey(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return 'artemisApp.exerciseUnit.delete.question';
            case LectureUnitType.ATTACHMENT:
                return 'artemisApp.attachmentUnit.delete.question';
            case LectureUnitType.VIDEO:
                return 'artemisApp.videoUnit.delete.question';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.question';
            case LectureUnitType.ONLINE:
                return 'artemisApp.onlineUnit.delete.question';
            default:
                return '';
        }
    }

    getDeleteConfirmationTextKey(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return 'artemisApp.exerciseUnit.delete.typeNameToConfirm';
            case LectureUnitType.ATTACHMENT:
                return 'artemisApp.attachmentUnit.delete.typeNameToConfirm';
            case LectureUnitType.VIDEO:
                return 'artemisApp.videoUnit.delete.typeNameToConfirm';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.typeNameToConfirm';
            case LectureUnitType.ONLINE:
                return 'artemisApp.onlineUnit.delete.typeNameToConfirm';
            default:
                return '';
        }
    }

    getActionType(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return ActionType.Unlink;
        } else {
            return ActionType.Delete;
        }
    }

    deleteLectureUnit(lectureUnitId: number) {
        this.lectureUnitService.delete(lectureUnitId, this.lectureId!).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    editButtonAvailable(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
            case LectureUnitType.TEXT:
            case LectureUnitType.VIDEO:
            case LectureUnitType.ONLINE:
                return true;
            default:
                return false;
        }
    }

    onEditButtonClicked(lectureUnit: LectureUnit) {
        this.onEditLectureUnitClicked.emit(lectureUnit);
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.ATTACHMENT:
                return (<AttachmentUnit>lectureUnit)?.attachment?.releaseDate || undefined;
            case LectureUnitType.EXERCISE:
                return (<ExerciseUnit>lectureUnit)?.exercise?.releaseDate || undefined;
            default:
                return lectureUnit.releaseDate || undefined;
        }
    }

    getAttachmentVersion(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.ATTACHMENT:
                return (<AttachmentUnit>lectureUnit)?.attachment?.version || undefined;
            default:
                return undefined;
        }
    }
    onIngestButtonClicked(lectureUnitId: number) {
        this.lectureUnitService.ingestLectureUnitInPyris(lectureUnitId, this.lecture.id!).subscribe({
            error: (error) => console.error('Failed to send Ingestion request', error),
        });
    }

    getIcon(attachmentUnit: AttachmentUnit): IconDefinition {
        switch (attachmentUnit.pyrisIngestionState) {
            case IngestionState.NOT_STARTED:
                return this.sendToIris;
            case IngestionState.IN_PROGRESS:
                return this.loading;
            case IngestionState.DONE:
                return this.done;
            case IngestionState.ERROR:
                return this.resendToIris;
            default:
                return this.sendToIris;
        }
    }
}
