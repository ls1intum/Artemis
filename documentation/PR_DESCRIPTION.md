**### Checklist
#### General
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [x] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.cit.tum.de/dev/development-process/development-process.html#naming-conventions-for-github-pull-requests).

#### Client
- [x] I **strictly** followed the [client coding guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client/).
- [x] I added multiple integration tests (Jest) related to the features (with a high test coverage), while following the [test guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client-tests/).
- [x] I documented the TypeScript code using JSDoc style.

### Motivation and Context
When users choose "Remind Me in 30 Days" on the passkey setup modal, this preference should persist across logout/login sessions. 
Previously, the `LocalStorageService.clear()` method cleared all local storage on logout, including the reminder preference stored under `EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY`. 
This caused the modal to appear again immediately after re-login, despite the user's explicit choice to be reminded later.

This PR fixes the bug by introducing selective local storage clearing that preserves certain user preferences (like the passkey reminder date) across logout sessions.

### Description
1. **LocalStorageService Enhancements** (`local-storage.service.ts:52-80`):
   - Modified the `clear()` method to preserve specific keys (currently `EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY`)
   - Added new `clearExcept(keysToPreserve: string[])` method that:
     - Saves values of keys to preserve before clearing
     - Clears all localStorage
     - Restores the preserved values
   - This allows user preferences to persist across logout/login cycles while still clearing session-specific data

2. **Test Coverage**:
   - Added 4 new Jest unit tests for `LocalStorageService` (`local-storage.service.spec.ts:55-106`):
   - Added E2E Playwright test (`PasskeyReminderPersistence.spec.ts`):
     - Verifies the complete user flow: login → dismiss modal with "Remind Me in 30 Days" → logout → login again
     - Ensures modal does not reappear on second login

### Steps for Testing
Prerequisites:
- 1 User without a registered passkey  (you can clear that in the user settings, delete all passkeys for that user)
- If you want to repeat the test, make sure to clear `earliestSetupPasskeyReminderDate` in the local storage in case you already clicked "Remind Me in 30 Days" before.

**Manual Testing:**
1. Log in to Artemis with an admin account
2. When the passkey setup modal appears, click "Remind Me in 30 Days"
3. Verify the modal closes and you're navigated to the courses page
4. Log out using the navigation bar
5. Log in again with the same account
6. Verify that the passkey setup modal does NOT appear again
7. Check browser console/DevTools → Application → Local Storage and confirm `earliestSetupPasskeyReminderDate` key exists with a future date

### Testserver States
You can manage test servers using [Helios](https://helios.aet.cit.tum.de/). Check environment statuses in the [environment list](https://helios.aet.cit.tum.de/repo/69562331/environment/list). To deploy to a test server, go to the [CI/CD](https://helios.aet.cit.tum.de/repo/69562331/ci-cd) page, find your PR or branch, and trigger the deployment.

### Review Progress

#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2

#### Manual Tests
- [ ] Test 1

### Test Coverage**
