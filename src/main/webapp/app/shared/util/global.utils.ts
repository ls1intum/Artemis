import { Signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';

/**
 * Use alert service to show the error message from the error response
 * @param alertService the service used to show the exception messages to the user
 * @param error the error response that's status is used to determine the error message
 * @param disableTranslation whether the error message should be translated
 */
export const onError = (alertService: AlertService, error: HttpErrorResponse, disableTranslation: boolean = true) => {
    switch (error.status) {
        case 400:
            alertService.error('error.http.400');
            break;
        case 403:
            alertService.error('error.http.403');
            break;
        case 404:
            alertService.error('error.http.404');
            break;
        case 405:
            alertService.error('error.http.405');
            break;
        case 500:
            // Removed to avoid alerts about internal errors as the user can't do anything about it
            // and the alert does not provide any other value
            break;
        default:
            alertService.addAlert({
                type: AlertType.DANGER,
                message: error.message,
                disableTranslation: disableTranslation,
            });
            break;
    }
};

/**
 * Error alerts from the server do already have a user-friendly message defined via the errorKey which is handled by
 * {@link AlertService}, we therefore do not want to show a generic error message in addition to that.
 *
 * @param error which was received
 */
export function isErrorAlert(error: any) {
    if (!error) {
        return false;
    }

    return !!error.error?.errorKey;
}

export function getCurrentLocaleSignal(translateService: TranslateService): Signal<string> {
    return toSignal(translateService.onLangChange.pipe(map((event) => event.lang)), {
        initialValue: translateService.getCurrentLang(),
    });
}

/**
 * Generates dot-notation string literal types for all property paths of an object `T`, up to a specified depth `D`.
 * Automatically unwraps arrays and optional properties, and prevents infinite loops by stopping at specific types (e.g., `Date`).
 *
 * @example
 * interface User {
 *     login?: string;
 *     orders: { id: number }[];
 * }
 * type UserPath = Path<User, 1>; // "login" | "orders" | "orders.id"
 *
 * // Angular Usage:
 * globalFilterFields: Path<ExamUser, 1>[] = ['id', 'user.login', 'exam.title'];
 */
export type Path<T, D extends number = 3> = [D] extends [never]
    ? never // We hit the depth limit, stop recursing.
    : T extends object
      ? {
            [K in keyof T & string]: NonNullable<T[K]> extends StopTypes // If it's a Date/Function, stop here and just return the key.
                ? K
                : NonNullable<T[K]> extends (infer U)[] // If it's an Array, unpack it and decrement depth.
                  ? K | `${K}.${Path<U, Prev[D]>}`
                  : NonNullable<T[K]> extends object // If it's an Object, unpack it and decrement depth.
                    ? K | `${K}.${Path<NonNullable<T[K]>, Prev[D]>}`
                    : K; // Base case (primitives like string, number, boolean)
        }[keyof T & string]
      : never;

type Prev = [never, 0, 1, 2, 3, 4, 5];
// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
type StopTypes = Date | Function | RegExp;
