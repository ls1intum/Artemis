/**
 * The suite reads dates off the Angular app's models, and those models are typed against the dayjs plugins the app
 * registers in `app/core/config/dayjs`. Pulling that module in for its types alone gives this project the same
 * augmented `Dayjs` the app has, so `isSameOrAfter`, `isBetween` and `dayjs.max` type check here as well. Nothing is
 * imported at run time: the suite extends the plugins it needs itself.
 */
import 'app/core/config/dayjs';
