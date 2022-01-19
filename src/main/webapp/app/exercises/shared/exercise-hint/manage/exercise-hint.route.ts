import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { TextHintService } from './text-hint.service';
import { TextHintComponent } from './exercise-hint.component';
import { TextHintDetailComponent } from './exercise-hint-detail.component';
import { TextHintUpdateComponent } from './exercise-hint-update.component';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';
import { TextHint } from 'app/entities/hestia/text-hint-model';

@Injectable({ providedIn: 'root' })
export class TextHintResolve implements Resolve<ExerciseHint> {
    constructor(private service: TextHintService) {}

    /**
     * Resolves the route into a text hint id and fetches it from the server
     * @param route Route which to resolve
     */
    resolve(route: ActivatedRouteSnapshot) {
        const id = route.params['hintId'] ? route.params['hintId'] : undefined;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<TextHint>) => response.ok),
                map((textHint: HttpResponse<TextHint>) => textHint.body!),
            );
        }
        return of(new TextHint());
    }
}

export const textHintRoute: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/text-hints/new',
            component: TextHintUpdateComponent,
            resolve: {
                textHint: TextHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.textHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/text-hints/:hintId',
            component: TextHintDetailComponent,
            resolve: {
                textHint: TextHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.textHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/text-hints/:hintId/edit',
            component: TextHintUpdateComponent,
            resolve: {
                textHint: TextHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.textHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/text-hints',
            component: TextHintComponent,
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.textHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
