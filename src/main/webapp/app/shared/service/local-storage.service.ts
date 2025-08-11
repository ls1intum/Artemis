import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LocalStorageService {
    /**
     * Stores a value in the local storage under the specified key.
     * Refer to the comments in the retrieve method to decide how to use this method.
     * @param key The key under which the value will be stored.
     * @param value The value to be stored, which will be stringified.
     */
    store<T>(key: string, value: T) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    /**
     * Retrieves a value from the local storage by its key.
     * Does not support retrieving objects of type {@link Date}.
     * For complex objects that can not be parsed by this method consider serializing
     * the object to a string and storing/retrieving it as such.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value of type T or undefined if not found.
     */
    retrieve<T>(key: string): T | undefined {
        const value = localStorage.getItem(key);
        return value ? (JSON.parse(value) as T) : undefined;
    }

    /**
     * Retrieves a Date object from the local storage by its key.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value or undefined if not found or parsing failed.
     */
    retrieveDate(key: string): Date | undefined {
        const raw = localStorage.getItem(key);
        if (!raw) return undefined;
        const isoString = JSON.parse(raw);
        const date = new Date(isoString);
        // check whether a valid date could be parsed and avoid parsing dates from non-ISO-representations
        return !isNaN(date.getTime()) && isoString === date.toISOString() ? date : undefined;
    }

    /**
     * Removes a specific key from the local storage.
     * @param key The key to remove from local storage.
     */
    remove(key: string) {
        localStorage.removeItem(key);
    }

    /**
     * Clears the entire local storage.
     */
    clear() {
        localStorage.clear();
    }
}
