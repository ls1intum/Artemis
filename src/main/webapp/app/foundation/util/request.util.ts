import { HttpParams } from '@angular/common/http';

/** A value that can be serialized into an {@link HttpParams} entry. */
type RequestOptionValue = string | number | boolean;

/**
 * A pageable/sortable query object whose properties become request parameters. `sort` is treated specially and
 * appended once per entry. Some callers pass a bare value (e.g. a course id) instead of an object, so this is
 * intentionally permissive; a non-object argument produces an empty {@link HttpParams}.
 */
type RequestOptions = { sort?: RequestOptionValue[] } & Record<string, RequestOptionValue | RequestOptionValue[]>;

export const createRequestOption = (req?: RequestOptions | RequestOptionValue): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req && typeof req === 'object') {
        Object.keys(req).forEach((key) => {
            if (key !== 'sort') {
                options = options.set(key, req[key] as RequestOptionValue);
            }
        });
        if (req.sort) {
            req.sort.forEach((val) => {
                options = options.append('sort', val);
            });
        }
    }
    return options;
};

export const createNestedRequestOption = (req?: RequestOptions | RequestOptionValue, parentKey?: string): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req && typeof req === 'object') {
        Object.keys(req).forEach((key) => {
            if (key !== 'sort') {
                const optionKey = parentKey ? `${parentKey}.${key}` : key;
                options = options.set(optionKey, req[key] as RequestOptionValue);
            }
        });
        if (req.sort) {
            req.sort.forEach((val) => {
                const optionKey = parentKey ? `${parentKey}.sort` : 'sort';
                options = options.append(optionKey, val);
            });
        }
    }
    return options;
};
