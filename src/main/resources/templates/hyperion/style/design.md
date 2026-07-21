# DESIGN.md (the design document)

Role: your working memory and the contract every later stage is checked against. Written before any code;
updated whenever a later stage forces a design change, so it always describes the final exercise truthfully.
Everything downstream — template shape, test seams, statement tasks, the diagram — is derived from it, so an
ambiguity here becomes a defect later.

## What each section must pin (and why)

- `## Classes` — one row per type with its role and template status (given complete / stubbed / student
  creates it). For every piece of MUTABLE STATE, say which class owns it and whether it survives object
  replacement (a swap, reset, or re-registration): tests may only demand what this ownership makes possible,
  and an unpinned seam here is how a solution ends up contradicting its own design.
- `## Public API` — signatures only. This is the single place the API is designed; later stages copy it, they
  do not renegotiate it silently.
- `## Tasks` — one row per SEAM (an independently actionable unit of student work), listing the behaviour
  partitions its tests will need. Never one row per test; never one row for the whole exercise unless it is
  genuinely one seam.
- `## Diagram` — an honest yes/no with a reason grounded in the DESIGN: yes when several collaborating types
  or student-created types make an architecture overview genuinely helpful; no when a single class carries
  the work. The reason must follow from the design itself — "no, because the statement has no diagram" is
  circular and invalid; the statement follows this decision, not the other way around.

## What may vary

Table columns, row order, and formatting are free. Extra sections (open questions, rejected alternatives)
are welcome while drafting but must be resolved or removed before the design gate. The design's SIZE follows
the brief: do not inflate a one-class exercise into a pattern, and do not collapse a pattern exercise into
one class.

## Exemplar (FORM only — never copy its topic, API, or design)

```markdown
## Classes
| Name | Role | Template status |
|------|------|-----------------|
| LoyaltyAccount | Owns the mutable points balance; balance survives strategy swaps because the account, not the strategy, stores it | student-implements-stubbed |
| RewardStrategy | Strategy interface: computes points for one purchase | student-creates-absent-from-template |
| FlatRateStrategy | 1 point per whole dollar | student-creates-absent-from-template |
| Purchase | Immutable amount holder | given-complete-in-template |

## Public API
- `interface RewardStrategy { int pointsFor(Purchase purchase); }`
- `class LoyaltyAccount { void setStrategy(RewardStrategy s); void record(Purchase p); int getPoints(); }`

## Tasks
| Seam | Partitions its tests need |
|------|--------------------------|
| Implement flat-rate rewards | typical amount, sub-dollar amount rounds down to zero |
| Wire strategy into the account | recording delegates to the current strategy; swapping strategies keeps the balance |

## Diagram
Yes — students create the strategy interface and one implementation and must see how they plug into the
provided account class.
```

An equally honest "no": `No — the whole exercise is one class with two methods; a diagram would restate two
signatures.`
