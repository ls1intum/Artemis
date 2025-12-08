import { Injectable } from '@angular/core';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';

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
     * Clears the entire local storage except for {@link KEYS_TO_PRESERVE}.
     */
    clear() {
        // Keys that should persist across logouts (user preferences, reminders, etc.)
        const KEYS_TO_PRESERVE = [EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY];

        this.clearExcept(KEYS_TO_PRESERVE);
    }

    /**
     * Clears the local storage except for the specified keys that should persist across logouts.
     * @param keysToPreserve Array of keys that should not be cleared.
     */
    clearExcept(keysToPreserve: string[]) {
        // Save values of keys to preserve
        const preservedValues = new Map<string, string | null>();
        keysToPreserve.forEach((key) => {
            preservedValues.set(key, localStorage.getItem(key));
        });
        // Clear all localStorage
        localStorage.clear();

        // Restore preserved values
        preservedValues.forEach((value, key) => {
            if (value !== null) {
                localStorage.setItem(key, value);
            }
        });
    }
}
