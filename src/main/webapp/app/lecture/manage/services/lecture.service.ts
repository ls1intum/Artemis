import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';
import { AccountService } from 'app/core/auth/account.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { LectureApi } from 'app/openapi/api/lecture-api';
import type { SimpleLecture } from 'app/openapi/model/simple-lecture';
import type { GetLectures } from 'app/openapi/model/get-lectures';
import { toLecture, toLectureForOverview, toSimpleLectureRequest } from 'app/lecture/shared/util/generated-lecture.util';

@Service()
export class LectureService {
    private readonly lectureApi = inject(LectureApi);
    private accountService = inject(AccountService);
    private entityTitleService = inject(EntityTitleService);

    currentTutorialLectureId: number | undefined = undefined;

    create(lecture: Lecture): Observable<Lecture> {
        return this.lectureApi.createLecture(toSimpleLectureRequest(lecture)).pipe(map(toLecture));
    }

    update(lecture: Lecture): Observable<Lecture> {
        return this.lectureApi.updateLecture(toSimpleLectureRequest(lecture)).pipe(
            map(toLecture),
            // The editor stays open after saving, so the breadcrumb and the page title have to show a new title right away.
            tap((updated) => this.sendTitlesToEntityTitleService(updated)),
        );
    }

    find(lectureId: number): Observable<Lecture> {
        return this.lectureApi.getLecture(lectureId).pipe(
            map(toLecture),
            tap((lecture) => this.setAccessRightsAndTitle(lecture)),
        );
    }

    findWithDetails(lectureId: number): Observable<Lecture> {
        return this.lectureApi.getLectureWithDetails(lectureId).pipe(
            map((details) => {
                const lecture = toLecture(details);
                // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                lecture.posts ??= [];
                return lecture;
            }),
            tap((lecture) => this.setAccessRightsAndTitle(lecture)),
        );
    }

    findAllByCourseId(courseId: number): Observable<Lecture[]> {
        return this.lectureApi.getLecturesForCourse(courseId).pipe(map((lectures) => this.toLecturesWithAccessRightsAndTitles(lectures)));
    }

    /**
     * Fetches the lectures of a course for the student course overview: the sidebar fields only, without attachments
     * or lecture units, which are loaded when a single lecture is opened.
     * These used to arrive as part of the course itself, which made every course visit pay for them.
     * @param courseId the course to fetch the lectures for
     */
    findAllByCourseIdForOverview(courseId: number): Observable<LectureForOverview[]> {
        return this.lectureApi.getLecturesForCourseOverview(courseId).pipe(
            map((lectures) => lectures.map(toLectureForOverview)),
            tap((lectures) => lectures.forEach((lecture) => this.entityTitleService.setTitle(EntityType.LECTURE, [lecture.id], lecture.title))),
        );
    }

    findAllTutorialLecturesByCourseId(courseId: number): Observable<Lecture[]> {
        return this.lectureApi.getTutorialLecturesForCourse(courseId).pipe(map((lectures) => this.toLecturesWithAccessRightsAndTitles(lectures)));
    }

    // only loads attachment units, no other units
    findAllByCourseIdWithSlides(courseId: number): Observable<Lecture[]> {
        return this.lectureApi.getLecturesWithSlidesForCourse(courseId).pipe(map((lectures) => this.toLecturesWithAccessRightsAndTitles(lectures)));
    }

    /**
     * Clones and imports the lecture to the course
     *
     * @param courseId Course to import the lecture into
     * @param lectureId Lecture to be cloned and imported
     */
    import(courseId: number, lectureId: number): Observable<Lecture> {
        return this.lectureApi.importLecture(lectureId, courseId).pipe(
            map(toLecture),
            tap((lecture) => this.setAccessRightsAndTitle(lecture)),
        );
    }

    private toLecturesWithAccessRightsAndTitles(generatedLectures: (SimpleLecture | GetLectures)[]): Lecture[] {
        const lectures = generatedLectures.map(toLecture);
        lectures.forEach((lecture) => this.setAccessRightsAndTitle(lecture));
        return lectures;
    }

    private setAccessRightsAndTitle(lecture: Lecture) {
        this.setAccessRightsLecture(lecture);
        this.sendTitlesToEntityTitleService(lecture);
    }

    /**
     * Besides the within the lecture included variables for access rights the access rights of the
     * respective course are set as well.
     *
     * @param lecture for which the access rights shall be set
     */
    private setAccessRightsLecture(lecture: Lecture) {
        if (lecture.course) {
            this.accountService.setAccessRightsForCourse(lecture.course);
            lecture.isAtLeastEditor = lecture.course.isAtLeastEditor;
            lecture.isAtLeastInstructor = lecture.course.isAtLeastInstructor;
        }
    }

    private sendTitlesToEntityTitleService(lecture: Lecture) {
        this.entityTitleService.setTitle(EntityType.LECTURE, [lecture.id], lecture.title);
    }
}
