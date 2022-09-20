import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

export const simpleOneLayerActivatedRouteProvider = (params: Map<string, number> = new Map<string, number>(), data: Object = {}) => {
    return {
        provide: ActivatedRoute,
        useValue: {
            data: of(data),
            paramMap: of({
                get: (key: string) => {
                    return params.get(key);
                },
            }),
        },
    };
};

export const simpleTwoLayerActivatedRouteProvider = (
    params: Map<string, number> = new Map<string, number>(),
    parentParams: Map<string, number> = new Map<string, number>(),
    data: Object = {},
) => {
    return {
        provide: ActivatedRoute,
        useValue: {
            data: of(data),
            paramMap: of({
                get: (key: string) => {
                    return params.get(key);
                },
            }),
            parent: {
                paramMap: of({
                    get: (key: string) => {
                        return parentParams.get(key);
                    },
                }),
            },
        },
    };
};
