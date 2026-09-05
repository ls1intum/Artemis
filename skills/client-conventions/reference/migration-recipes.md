# Client migration recipes

Before-and-after for the conversions that come up most, with the reasoning where the mechanical
translation is wrong.

## `@Input` to `input()`

```typescript
// before
@Input() course: Course;
@Input() required = false;

// after
readonly course = input.required<Course>();
readonly required = input(false);
```

Reads become calls: `this.course()` rather than `this.course`. In templates, `course()` likewise.

A two-way binding becomes `model()` rather than an `input()` plus an `output()`. Using the pair
where a `model()` is meant is a common mistake that only shows up when the parent stops receiving
updates.

## `@Output` to `output()`

```typescript
// before
@Output() saved = new EventEmitter<Course>();
this.saved.emit(course);

// after
readonly saved = output<Course>();
this.saved.emit(course);
```

## `@ViewChild` to `viewChild()`

```typescript
// before
@ViewChild('editor') editor: ElementRef;

// after
readonly editor = viewChild.required<ElementRef>('editor');
```

Use `viewChild()` when the child may be absent, `viewChild.required()` when it must exist.

## Constructor injection to `inject()`

```typescript
// before
constructor(private courseService: CourseService) {}

// after
private readonly courseService = inject(CourseService);
```

## `ngOnChanges` to `computed()` or `effect()`

Deriving a value from inputs is a `computed()`:

```typescript
// before
ngOnChanges() {
    this.visibleExercises = this.exercises.filter((e) => e.visible);
}

// after
readonly visibleExercises = computed(() => this.exercises().filter((e) => e.visible));
```

Reacting with a side effect is an `effect()`. Prefer `computed()` wherever the result is a value:
an `effect()` that only assigns a field is a `computed()` written the hard way.

Only `SimpleChanges.previousValue`, `isFirstChange()`, and ordering before child initialisation
genuinely need the hook. Those need a comment and a line-level disable.

## Cloning, and how it interacts with signals

The rule is `deepClone`, but the interesting part is when to copy at all.

**Replacing an object in a signal.** A signal notifies only when the reference changes, so replace
rather than mutate:

```typescript
const updated = deepClone(current);
updated.field = value;
return updated;
```

The canonical example is `setImageUrl` in `src/main/webapp/app/core/auth/account.service.ts`.

**When you only need the signal to emit.** Do not copy at all. Declare the signal with
`equal: () => false` and re-set the same reference. Copying detaches the nested objects that
children already hold, and that ends in `NG0103`.

**When the state is not signal-backed.** Build the replacement explicitly, field by field, rather
than reaching for a shallow copy.

**Single-expression copy with overrides.** `cloneWith(x, { a, b })` instead of `{ ...x, a, b }`. The
source is deep-cloned and the overrides are applied by reference.

**Giving a parsed DTO its prototype.** `hydrate(new Course(), dto)` instead of
`Object.assign(new Course(), dto)`.

Full rationale with more examples:
`documentation/docs/developer/guidelines/client-development.mdx`.

## Template control flow

```angular-html
<!-- before -->
<div *ngIf="course">{{ course.title }}</div>
<li *ngFor="let e of exercises; trackBy: trackId">{{ e.title }}</li>

<!-- after -->
@if (course()) {
    <div>{{ course()!.title }}</div>
}
@for (e of exercises(); track e.id) {
    <li>{{ e.title }}</li>
}
```

`@for` requires `track`. It is not optional the way `trackBy` was.

## Colours

```html
<!-- wrong: primitive, Bootstrap class, superseded arbitrary form -->
<span class="text-red-500">…</span>
<span class="text-danger">…</span>
<span class="text-(--danger)">…</span>

<!-- right: semantic token -->
<span class="text-state-danger">…</span>
```

For a component, prefer the TUM UI variant over a utility class on plain markup.

## Verifying a migration

```bash
pnpm run lint
pnpm exec vitest run <path-to-spec>
```

A template-only error will not show up in either. Run a production build when a template changed
structurally:

```bash
pnpm run webapp:prod
```
