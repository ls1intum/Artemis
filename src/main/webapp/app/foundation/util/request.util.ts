import { HttpParams } from '@angular/common/http';

/** A value that can be serialized into an {@link HttpParams} entry. */
type RequestOptionValue = string | number | boolean;

/**
 * A pageable/sortable query object whose properties become request parameters. `sort` is treated specially and
 * appended once per entry. Some callers pass a bare value (e.g. a course id) instead of an object, so this is
 * intentionally permissive; a non-object argument produces an empty {@link HttpParams}.
 */
type RequestOptions = { sort?: RequestOptionValue[] } & Record<string, RequestOptionValue | RequestOptionValue[]>;

export const createRequestOption = <T extends object>(req?: T | RequestOptionValue): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req && typeof req === 'object') {
        // Callers pass a variety of typed query/option objects; treat them uniformly as request parameters.
        const request = req as RequestOptions;
        Object.keys(request).forEach((key) => {
            if (key !== 'sort') {
                options = options.set(key, request[key] as RequestOptionValue);
            }
        });
        if (request.sort) {
            request.sort.forEach((val) => {
                options = options.append('sort', val);
            });
        }
    }
    return options;
};

export const createNestedRequestOption = <T extends object>(req?: T | RequestOptionValue, parentKey?: string): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req && typeof req === 'object') {
        const request = req as RequestOptions;
        Object.keys(request).forEach((key) => {
            if (key !== 'sort') {
                const optionKey = parentKey ? `${parentKey}.${key}` : key;
                options = options.set(optionKey, request[key] as RequestOptionValue);
            }
        });
        if (request.sort) {
            request.sort.forEach((val) => {
                const optionKey = parentKey ? `${parentKey}.sort` : 'sort';
                options = options.append(optionKey, val);
            });
        }
    }
    return options;
};
