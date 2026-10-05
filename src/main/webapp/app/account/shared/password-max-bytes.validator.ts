import { ValidatorFn } from '@angular/forms';
import { PASSWORD_MAX_BYTES } from 'app/app.constants';

/** Reject new passwords exceeding BCrypt's UTF-8 byte limit, independently of their character count. */
export const passwordMaxBytesValidator: ValidatorFn = (control) => (new TextEncoder().encode(control.value).length > PASSWORD_MAX_BYTES ? { maxbytes: true } : null);
