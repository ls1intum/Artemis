import { Service } from '@angular/core';
import { parseJson } from 'app/foundation/util/json.util';

/** Earliest date on which the setup passkey modal reminds the user again. Kept across logouts, see {@link LocalStorageService.clear}. */
export const EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY = 'earliestSetupPasskeyReminderDate';

/** Per-user browser preferences, retained through logout without retaining authentication or exercise data. */
export const BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX = 'buildNotifications.';

@Service()
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
     * A stored value that is not valid JSON, for example one written by an older version of the client, is removed and treated as absent.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value of type T or undefined if not found or not readable.
     */
    retrieve<T>(key: string): T | undefined {
        const value = this.read(key);
        if (!value) {
            return undefined;
        }
        try {
            return parseJson<T>(value);
        } catch {
            this.remove(key);
            return undefined;
        }
    }

    /**
     * Retrieves a Date object from the local storage by its key.
     * <p>
     * A date is stored as a JSON string, i.e. with quotes. Older versions of the client stored the bare ISO string, which is still accepted and
     * rewritten in the current format. Any other value is corrupt: only its key is removed.
     * @param key The key to retrieve the value from local storage.
     * @returns The parsed value or undefined if not found, not readable or not a valid date.
     */
    retrieveDate(key: string): Date | undefined {
        const raw = this.read(key);
        if (!raw) {
            return undefined;
        }
        let stored: unknown;
        try {
            stored = parseJson<unknown>(raw);
        } catch {
            // not JSON: the bare ISO string of an older client, or garbage
            const legacyDate = LocalStorageService.parseIsoDate(raw);
            if (legacyDate) {
                this.migrateToJson(key, raw);
            } else {
                this.remove(key);
            }
            return legacyDate;
        }
        const date = typeof stored === 'string' ? LocalStorageService.parseIsoDate(stored) : undefined;
        if (!date) {
            this.remove(key);
        }
        return date;
    }

    /**
     * Parses a date from its ISO representation and rejects every other representation, which the Date constructor would still interpret.
     */
    private static parseIsoDate(isoString: string): Date | undefined {
        const date = new Date(isoString);
        return !isNaN(date.getTime()) && isoString === date.toISOString() ? date : undefined;
    }

    /**
     * Reads the raw value of a key. The storage can be absent or refuse access (e.g. in embedded web views or with blocked site data), which is treated as an absent value.
     */
    private read(key: string): string | null {
        try {
            return localStorage.getItem(key);
        } catch {
            return null;
        }
    }

    private migrateToJson(key: string, isoString: string) {
        try {
            localStorage.setItem(key, JSON.stringify(isoString));
        } catch {
            // keep the old value: it is read again the same way
        }
    }

    /**
     * Removes a specific key from the local storage.
     * @param key The key to remove from local storage.
     */
    remove(key: string) {
        try {
            localStorage.removeItem(key);
        } catch {
            // the storage is not accessible, so there is nothing to remove
        }
    }

    /**
     * Clears the entire local storage except for specific keys that should persist across logouts.
     */
    clear() {
        // Keys that should persist across logouts (user preferences, reminders, etc.)
        const KEYS_TO_PRESERVE = [EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY];
        KEYS_TO_PRESERVE.push(...Object.keys(localStorage).filter((key) => key.startsWith(BUILD_NOTIFICATIONS_LOCAL_STORAGE_KEY_PREFIX)));

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
