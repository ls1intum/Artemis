import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SessionStorageService {
    /**
     * Stores a value in the session storage under the specified key.
     * Refer to the comments in the retrieve method to decide how to use this method.
     * @param key The key under which the value will be stored.
     * @param value The value to be stored, which will be stringified.
     */
    store<T>(key: string, value: T) {
        sessionStorage.setItem(key, JSON.stringify(value));
    }

    /**
     * Retrieves a value from the session storage by its key.
     * Does not support retrieving objects of type {@link Date}.
     * For complex objects that can not be parsed by this method consider serializing
     * the object to a string and storing/retrieving it as such.
     * @param key The key to retrieve the value from session storage.
     * @returns The parsed value of type T or undefined if not found.
     */
    retrieve<T>(key: string): T | undefined {
        const value = sessionStorage.getItem(key);
        return value ? (JSON.parse(value) as T) : undefined;
    }

    /**
     * Retrieves a Date object from the session storage by its key.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value or undefined if not found or parsing failed.
     */
    retrieveDate(key: string): Date | undefined {
        const raw = sessionStorage.getItem(key);
        if (!raw) return undefined;
        const isoString = JSON.parse(raw);
        const date = new Date(isoString);
        // check whether a valid date could be parsed and avoid parsing dates from non-ISO-representations
        return !isNaN(date.getTime()) && isoString === date.toISOString() ? date : undefined;
    }

    /**
     * Removes a specific key from the session storage.
     * @param key The key to remove from session storage.
     */
    remove(key: string) {
        sessionStorage.removeItem(key);
    }

    /**
     * Clears the entire session storage.
     */
    clear() {
        sessionStorage.clear();
    }
}
