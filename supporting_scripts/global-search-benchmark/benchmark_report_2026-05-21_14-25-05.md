# Global Search Benchmark Report

**Generated:** 2026-05-21 14:25:05 UTC

## How to Read This Report

Each query is issued `iterations` times without warm-up. The following latency statistics are reported per query:

| Metric | Meaning |
|--------|---------|
| **Min** | Fastest observed response time. |
| **Max** | Slowest observed response time. |
| **Mean** | Arithmetic average across all iterations. |
| **Median** | Middle value (50th percentile) — robust to outliers. |
| **p95** | 95th-percentile latency: 95 % of requests completed within this time. Captures typical tail latency. |
| **p99** | 99th-percentile latency: 99 % of requests completed within this time. Captures worst-case tail latency. |

> **Note:** With a small iteration count (e.g. 20) the p95 and p99 values collapse to the observed maximum because there are too few samples to distinguish the top 5 % from the top 1 %. Increase `iterations` in `config.ini` for more meaningful percentile resolution.

> **Measurement scope:** All latency values are **end-to-end wall-clock times** measured from the benchmark client. Each request travels: benchmark client → Artemis Spring endpoint (`GET /api/search`) → Weaviate → Spring response → client. The numbers therefore include HTTP overhead, Spring controller processing, and Weaviate query time. Weaviate is queried *directly* only for the entity-count snapshot reported in the [Ingested Entities](#ingested-entities-in-weaviate) section; those calls are not part of the latency benchmark.

## Environment

| Parameter | Value |
|-----------|-------|
| Artemis server | `http://localhost:8080/api` |
| Weaviate | `http://localhost:8001` |
| Weaviate collection | `Artemis_SearchableEntities` |
| Iterations per query | 20 |
| Result limit per call | 10 |

## Ingested Entities in Weaviate

Counts were captured immediately before the benchmark run.

| Entity Type | Count |
|-------------|------:|
| `exercise` | 5 |
| `lecture` | 0 |
| `lecture_unit` | 0 |
| `exam` | 0 |
| `faq` | 0 |
| `channel` | 1 |
| `course` | 1 |
| `post` | 5 |
| `answer_post` | 1 |
| **Total** | **13** |

## Benchmark Results — All Types Combined (`types=all`)

All latency values are in milliseconds (ms).

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 12.8 | 16.9 | 13.7 | 13.3 | 16.9 | 16.9 |
| generic: programming | 9.1 | 10.3 | 9.7 | 9.7 | 10.3 | 10.3 |
| generic: quiz | 8.3 | 10.0 | 9.0 | 8.8 | 10.0 | 10.0 |
| generic: lecture notes | 12.1 | 32.6 | 14.6 | 12.9 | 32.6 | 32.6 |
| generic: exam preparation | 11.4 | 13.8 | 12.3 | 12.2 | 13.8 | 13.8 |
| exercise: sorting algorithm | 11.5 | 16.9 | 13.9 | 13.8 | 16.9 | 16.9 |
| exercise: binary search tree | 10.2 | 19.9 | 12.4 | 11.7 | 19.9 | 19.9 |
| exercise: object-oriented design | 12.0 | 14.6 | 13.0 | 12.9 | 14.6 | 14.6 |
| lecture: introduction to databases | 11.1 | 18.9 | 12.6 | 12.4 | 18.9 | 18.9 |
| lecture: machine learning basics | 8.4 | 11.2 | 9.2 | 9.1 | 11.2 | 11.2 |
| exam: midterm | 9.2 | 10.8 | 9.9 | 9.9 | 10.8 | 10.8 |
| exam: final | 12.6 | 15.4 | 13.8 | 13.6 | 15.4 | 15.4 |
| faq: submission deadline | 12.8 | 15.9 | 14.4 | 14.3 | 15.9 | 15.9 |
| faq: grading criteria | 9.0 | 11.1 | 9.8 | 9.9 | 11.1 | 11.1 |
| channel: general announcements | 12.3 | 16.4 | 13.3 | 13.0 | 16.4 | 16.4 |
| channel: organization | 7.9 | 10.5 | 8.9 | 8.8 | 10.5 | 10.5 |
| course: software engineering | 11.1 | 19.2 | 12.5 | 12.2 | 19.2 | 19.2 |
| course: computer science | 10.8 | 12.8 | 11.7 | 11.5 | 12.8 | 12.8 |
| message: help with exercise | 10.5 | 13.4 | 11.9 | 11.8 | 13.4 | 13.4 |
| message: clarification needed | 11.2 | 12.5 | 11.6 | 11.5 | 12.5 | 12.5 |

## Benchmark Results — Per Entity Type

All latency values are in milliseconds (ms).

### `exercise`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 9.1 | 11.6 | 10.3 | 10.2 | 11.6 | 11.6 |
| generic: programming | 9.6 | 11.5 | 10.3 | 10.3 | 11.5 | 11.5 |
| generic: quiz | 9.3 | 11.3 | 10.1 | 10.0 | 11.3 | 11.3 |
| generic: lecture notes | 8.3 | 15.2 | 9.7 | 9.4 | 15.2 | 15.2 |
| generic: exam preparation | 9.1 | 11.9 | 9.9 | 9.7 | 11.9 | 11.9 |
| exercise: sorting algorithm | 9.0 | 15.6 | 11.4 | 11.4 | 15.6 | 15.6 |
| exercise: binary search tree | 9.3 | 11.8 | 10.4 | 10.4 | 11.8 | 11.8 |
| exercise: object-oriented design | 9.4 | 12.2 | 10.3 | 10.1 | 12.2 | 12.2 |
| lecture: introduction to databases | 9.2 | 24.5 | 10.8 | 10.0 | 24.5 | 24.5 |
| lecture: machine learning basics | 8.9 | 10.9 | 9.6 | 9.6 | 10.9 | 10.9 |
| exam: midterm | 9.0 | 10.9 | 10.2 | 10.4 | 10.9 | 10.9 |
| exam: final | 9.2 | 14.2 | 10.7 | 10.0 | 14.2 | 14.2 |
| faq: submission deadline | 9.8 | 11.9 | 10.6 | 10.5 | 11.9 | 11.9 |
| faq: grading criteria | 8.5 | 10.8 | 9.5 | 9.6 | 10.8 | 10.8 |
| channel: general announcements | 9.6 | 25.6 | 11.8 | 10.9 | 25.6 | 25.6 |
| channel: organization | 7.9 | 10.4 | 8.6 | 8.5 | 10.4 | 10.4 |
| course: software engineering | 8.3 | 11.0 | 9.3 | 9.0 | 11.0 | 11.0 |
| course: computer science | 8.3 | 18.6 | 9.6 | 9.0 | 18.6 | 18.6 |
| message: help with exercise | 7.9 | 10.3 | 8.9 | 8.7 | 10.3 | 10.3 |
| message: clarification needed | 8.3 | 10.5 | 8.9 | 8.8 | 10.5 | 10.5 |

### `lecture`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 5.6 | 7.1 | 6.1 | 5.9 | 7.1 | 7.1 |
| generic: programming | 6.9 | 12.1 | 7.9 | 7.6 | 12.1 | 12.1 |
| generic: quiz | 5.9 | 21.6 | 7.7 | 7.0 | 21.6 | 21.6 |
| generic: lecture notes | 5.6 | 6.9 | 6.0 | 5.8 | 6.9 | 6.9 |
| generic: exam preparation | 6.0 | 7.5 | 6.6 | 6.5 | 7.5 | 7.5 |
| exercise: sorting algorithm | 6.4 | 8.4 | 7.4 | 7.3 | 8.4 | 8.4 |
| exercise: binary search tree | 5.9 | 7.6 | 6.6 | 6.5 | 7.6 | 7.6 |
| exercise: object-oriented design | 7.4 | 11.5 | 8.3 | 8.0 | 11.5 | 11.5 |
| lecture: introduction to databases | 6.2 | 7.8 | 6.8 | 6.8 | 7.8 | 7.8 |
| lecture: machine learning basics | 6.1 | 7.4 | 6.7 | 6.6 | 7.4 | 7.4 |
| exam: midterm | 6.2 | 12.6 | 7.7 | 7.4 | 12.6 | 12.6 |
| exam: final | 6.0 | 7.9 | 7.1 | 7.3 | 7.9 | 7.9 |
| faq: submission deadline | 5.7 | 7.5 | 6.4 | 6.2 | 7.5 | 7.5 |
| faq: grading criteria | 5.9 | 59.7 | 15.6 | 8.8 | 59.7 | 59.7 |
| channel: general announcements | 6.4 | 10.9 | 7.5 | 7.3 | 10.9 | 10.9 |
| channel: organization | 5.3 | 7.0 | 5.9 | 5.8 | 7.0 | 7.0 |
| course: software engineering | 5.9 | 7.1 | 6.5 | 6.4 | 7.1 | 7.1 |
| course: computer science | 5.6 | 8.0 | 6.0 | 5.9 | 8.0 | 8.0 |
| message: help with exercise | 5.6 | 7.2 | 6.1 | 5.8 | 7.2 | 7.2 |
| message: clarification needed | 5.4 | 7.0 | 5.8 | 5.8 | 7.0 | 7.0 |

### `lecture_unit`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 5.9 | 7.3 | 6.5 | 6.5 | 7.3 | 7.3 |
| generic: programming | 5.8 | 11.9 | 7.3 | 7.3 | 11.9 | 11.9 |
| generic: quiz | 6.1 | 7.4 | 6.8 | 6.9 | 7.4 | 7.4 |
| generic: lecture notes | 5.6 | 7.1 | 6.4 | 6.3 | 7.1 | 7.1 |
| generic: exam preparation | 5.8 | 7.5 | 6.5 | 6.6 | 7.5 | 7.5 |
| exercise: sorting algorithm | 6.6 | 8.9 | 7.7 | 7.7 | 8.9 | 8.9 |
| exercise: binary search tree | 5.8 | 7.4 | 6.6 | 6.7 | 7.4 | 7.4 |
| exercise: object-oriented design | 6.9 | 8.4 | 7.5 | 7.4 | 8.4 | 8.4 |
| lecture: introduction to databases | 5.8 | 7.4 | 6.6 | 6.5 | 7.4 | 7.4 |
| lecture: machine learning basics | 6.0 | 7.8 | 6.7 | 6.6 | 7.8 | 7.8 |
| exam: midterm | 6.4 | 7.9 | 6.9 | 6.8 | 7.9 | 7.9 |
| exam: final | 6.2 | 7.8 | 7.1 | 7.2 | 7.8 | 7.8 |
| faq: submission deadline | 5.8 | 7.6 | 6.8 | 7.0 | 7.6 | 7.6 |
| faq: grading criteria | 6.7 | 47.6 | 12.8 | 8.1 | 47.6 | 47.6 |
| channel: general announcements | 5.7 | 7.7 | 6.4 | 6.3 | 7.7 | 7.7 |
| channel: organization | 5.3 | 6.5 | 5.6 | 5.5 | 6.5 | 6.5 |
| course: software engineering | 5.4 | 6.6 | 5.9 | 5.9 | 6.6 | 6.6 |
| course: computer science | 5.3 | 7.5 | 6.1 | 5.9 | 7.5 | 7.5 |
| message: help with exercise | 5.5 | 7.0 | 6.0 | 5.9 | 7.0 | 7.0 |
| message: clarification needed | 5.3 | 7.3 | 5.9 | 5.7 | 7.3 | 7.3 |

### `exam`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 5.9 | 7.8 | 6.6 | 6.6 | 7.8 | 7.8 |
| generic: programming | 5.7 | 7.7 | 6.7 | 6.7 | 7.7 | 7.7 |
| generic: quiz | 5.8 | 7.9 | 6.4 | 6.4 | 7.9 | 7.9 |
| generic: lecture notes | 5.8 | 7.7 | 6.6 | 6.6 | 7.7 | 7.7 |
| generic: exam preparation | 5.5 | 7.3 | 6.3 | 6.2 | 7.3 | 7.3 |
| exercise: sorting algorithm | 6.3 | 7.9 | 6.9 | 6.9 | 7.9 | 7.9 |
| exercise: binary search tree | 5.8 | 8.1 | 7.0 | 7.0 | 8.1 | 8.1 |
| exercise: object-oriented design | 5.6 | 13.2 | 7.1 | 6.6 | 13.2 | 13.2 |
| lecture: introduction to databases | 5.5 | 7.2 | 6.3 | 6.2 | 7.2 | 7.2 |
| lecture: machine learning basics | 6.0 | 7.6 | 6.7 | 6.6 | 7.6 | 7.6 |
| exam: midterm | 6.8 | 9.9 | 8.0 | 8.0 | 9.9 | 9.9 |
| exam: final | 5.7 | 9.2 | 7.0 | 6.8 | 9.2 | 9.2 |
| faq: submission deadline | 5.7 | 7.5 | 6.6 | 6.5 | 7.5 | 7.5 |
| faq: grading criteria | 5.8 | 7.8 | 6.7 | 6.6 | 7.8 | 7.8 |
| channel: general announcements | 5.8 | 8.2 | 6.7 | 6.6 | 8.2 | 8.2 |
| channel: organization | 5.2 | 7.4 | 5.8 | 5.7 | 7.4 | 7.4 |
| course: software engineering | 5.9 | 8.2 | 7.0 | 7.0 | 8.2 | 8.2 |
| course: computer science | 5.3 | 6.7 | 5.8 | 5.7 | 6.7 | 6.7 |
| message: help with exercise | 5.3 | 7.2 | 5.9 | 5.8 | 7.2 | 7.2 |
| message: clarification needed | 5.5 | 7.7 | 6.3 | 6.2 | 7.7 | 7.7 |

### `faq`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 5.8 | 7.1 | 6.5 | 6.5 | 7.1 | 7.1 |
| generic: programming | 5.7 | 7.6 | 6.5 | 6.5 | 7.6 | 7.6 |
| generic: quiz | 5.8 | 7.5 | 6.6 | 6.5 | 7.5 | 7.5 |
| generic: lecture notes | 5.9 | 6.9 | 6.4 | 6.4 | 6.9 | 6.9 |
| generic: exam preparation | 5.4 | 7.3 | 5.9 | 5.7 | 7.3 | 7.3 |
| exercise: sorting algorithm | 6.2 | 8.4 | 7.1 | 6.9 | 8.4 | 8.4 |
| exercise: binary search tree | 6.8 | 11.1 | 7.7 | 7.4 | 11.1 | 11.1 |
| exercise: object-oriented design | 5.7 | 7.8 | 6.6 | 6.5 | 7.8 | 7.8 |
| lecture: introduction to databases | 5.4 | 7.3 | 6.0 | 5.9 | 7.3 | 7.3 |
| lecture: machine learning basics | 6.0 | 11.7 | 7.0 | 6.8 | 11.7 | 11.7 |
| exam: midterm | 6.3 | 8.8 | 7.4 | 7.3 | 8.8 | 8.8 |
| exam: final | 6.7 | 7.7 | 7.1 | 7.0 | 7.7 | 7.7 |
| faq: submission deadline | 5.4 | 7.2 | 6.0 | 5.8 | 7.2 | 7.2 |
| faq: grading criteria | 5.4 | 7.7 | 6.0 | 5.8 | 7.7 | 7.7 |
| channel: general announcements | 6.0 | 7.4 | 6.8 | 6.9 | 7.4 | 7.4 |
| channel: organization | 5.3 | 6.6 | 5.6 | 5.5 | 6.6 | 6.6 |
| course: software engineering | 6.5 | 8.3 | 7.2 | 7.2 | 8.3 | 8.3 |
| course: computer science | 5.3 | 6.2 | 5.7 | 5.6 | 6.2 | 6.2 |
| message: help with exercise | 5.5 | 7.1 | 6.1 | 5.9 | 7.1 | 7.1 |
| message: clarification needed | 6.5 | 10.2 | 7.7 | 7.5 | 10.2 | 10.2 |

### `channel`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 8.7 | 26.3 | 11.7 | 10.1 | 26.3 | 26.3 |
| generic: programming | 6.1 | 8.1 | 6.8 | 6.8 | 8.1 | 8.1 |
| generic: quiz | 5.9 | 7.7 | 6.4 | 6.3 | 7.7 | 7.7 |
| generic: lecture notes | 6.1 | 12.2 | 7.1 | 6.7 | 12.2 | 12.2 |
| generic: exam preparation | 5.7 | 8.4 | 6.8 | 6.7 | 8.4 | 8.4 |
| exercise: sorting algorithm | 5.9 | 8.2 | 7.1 | 7.1 | 8.2 | 8.2 |
| exercise: binary search tree | 7.2 | 17.9 | 9.9 | 9.2 | 17.9 | 17.9 |
| exercise: object-oriented design | 8.7 | 10.4 | 9.5 | 9.5 | 10.4 | 10.4 |
| lecture: introduction to databases | 6.0 | 9.0 | 6.9 | 6.9 | 9.0 | 9.0 |
| lecture: machine learning basics | 7.0 | 11.1 | 8.0 | 7.7 | 11.1 | 11.1 |
| exam: midterm | 7.3 | 8.4 | 7.7 | 7.7 | 8.4 | 8.4 |
| exam: final | 6.0 | 8.3 | 7.3 | 7.4 | 8.3 | 8.3 |
| faq: submission deadline | 5.5 | 17.3 | 6.7 | 5.9 | 17.3 | 17.3 |
| faq: grading criteria | 5.6 | 6.8 | 6.1 | 6.0 | 6.8 | 6.8 |
| channel: general announcements | 8.8 | 12.2 | 10.0 | 9.8 | 12.2 | 12.2 |
| channel: organization | 5.6 | 8.1 | 6.3 | 6.0 | 8.1 | 8.1 |
| course: software engineering | 6.9 | 7.9 | 7.4 | 7.4 | 7.9 | 7.9 |
| course: computer science | 8.1 | 9.9 | 8.6 | 8.5 | 9.9 | 9.9 |
| message: help with exercise | 5.5 | 10.8 | 6.8 | 6.5 | 10.8 | 10.8 |
| message: clarification needed | 6.0 | 14.2 | 8.2 | 8.0 | 14.2 | 14.2 |

### `course`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 9.4 | 16.0 | 11.8 | 11.4 | 16.0 | 16.0 |
| generic: programming | 6.0 | 7.6 | 6.7 | 6.7 | 7.6 | 7.6 |
| generic: quiz | 5.9 | 7.3 | 6.5 | 6.5 | 7.3 | 7.3 |
| generic: lecture notes | 5.9 | 7.7 | 6.7 | 6.7 | 7.7 | 7.7 |
| generic: exam preparation | 5.9 | 8.3 | 6.9 | 6.8 | 8.3 | 8.3 |
| exercise: sorting algorithm | 6.2 | 10.0 | 7.2 | 7.1 | 10.0 | 10.0 |
| exercise: binary search tree | 5.8 | 7.9 | 6.5 | 6.3 | 7.9 | 7.9 |
| exercise: object-oriented design | 6.3 | 7.6 | 7.0 | 7.0 | 7.6 | 7.6 |
| lecture: introduction to databases | 6.3 | 8.0 | 6.9 | 6.8 | 8.0 | 8.0 |
| lecture: machine learning basics | 6.3 | 8.2 | 7.4 | 7.5 | 8.2 | 8.2 |
| exam: midterm | 6.6 | 8.8 | 7.7 | 7.6 | 8.8 | 8.8 |
| exam: final | 6.1 | 9.7 | 7.3 | 7.3 | 9.7 | 9.7 |
| faq: submission deadline | 5.7 | 7.8 | 6.1 | 5.9 | 7.8 | 7.8 |
| faq: grading criteria | 5.6 | 21.5 | 6.9 | 6.1 | 21.5 | 21.5 |
| channel: general announcements | 7.2 | 8.6 | 7.8 | 7.8 | 8.6 | 8.6 |
| channel: organization | 5.6 | 6.9 | 6.0 | 5.9 | 6.9 | 6.9 |
| course: software engineering | 6.9 | 9.3 | 7.4 | 7.3 | 9.3 | 9.3 |
| course: computer science | 5.6 | 7.1 | 6.2 | 6.1 | 7.1 | 7.1 |
| message: help with exercise | 6.8 | 11.4 | 7.5 | 7.3 | 11.4 | 11.4 |
| message: clarification needed | 5.6 | 7.2 | 6.1 | 5.9 | 7.2 | 7.2 |

### `post`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 11.0 | 14.6 | 12.7 | 12.7 | 14.6 | 14.6 |
| generic: programming | 6.3 | 7.5 | 6.8 | 6.8 | 7.5 | 7.5 |
| generic: quiz | 6.0 | 8.3 | 6.9 | 6.7 | 8.3 | 8.3 |
| generic: lecture notes | 10.7 | 14.2 | 12.1 | 11.9 | 14.2 | 14.2 |
| generic: exam preparation | 12.4 | 21.1 | 13.7 | 13.5 | 21.1 | 21.1 |
| exercise: sorting algorithm | 12.3 | 16.1 | 14.3 | 14.1 | 16.1 | 16.1 |
| exercise: binary search tree | 5.7 | 7.0 | 6.2 | 6.2 | 7.0 | 7.0 |
| exercise: object-oriented design | 11.2 | 30.4 | 13.4 | 12.6 | 30.4 | 30.4 |
| lecture: introduction to databases | 11.9 | 14.2 | 12.6 | 12.3 | 14.2 | 14.2 |
| lecture: machine learning basics | 6.1 | 8.6 | 7.0 | 7.0 | 8.6 | 8.6 |
| exam: midterm | 7.0 | 9.2 | 7.6 | 7.4 | 9.2 | 9.2 |
| exam: final | 12.3 | 18.3 | 14.3 | 14.2 | 18.3 | 18.3 |
| faq: submission deadline | 10.7 | 13.3 | 12.1 | 12.1 | 13.3 | 13.3 |
| faq: grading criteria | 6.2 | 8.1 | 7.0 | 6.8 | 8.1 | 8.1 |
| channel: general announcements | 11.2 | 31.7 | 14.4 | 13.5 | 31.7 | 31.7 |
| channel: organization | 5.6 | 6.3 | 5.9 | 5.9 | 6.3 | 6.3 |
| course: software engineering | 11.0 | 13.1 | 12.1 | 12.1 | 13.1 | 13.1 |
| course: computer science | 10.6 | 13.9 | 11.7 | 11.6 | 13.9 | 13.9 |
| message: help with exercise | 10.2 | 15.9 | 11.4 | 10.9 | 15.9 | 15.9 |
| message: clarification needed | 10.8 | 12.4 | 11.5 | 11.5 | 12.4 | 12.4 |

### `answer_post`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 11.5 | 14.3 | 12.4 | 12.3 | 14.3 | 14.3 |
| generic: programming | 5.5 | 22.6 | 6.9 | 5.9 | 22.6 | 22.6 |
| generic: quiz | 6.3 | 8.2 | 7.0 | 7.0 | 8.2 | 8.2 |
| generic: lecture notes | 5.8 | 7.2 | 6.1 | 6.0 | 7.2 | 7.2 |
| generic: exam preparation | 6.4 | 8.6 | 7.2 | 7.0 | 8.6 | 8.6 |
| exercise: sorting algorithm | 13.8 | 25.2 | 15.3 | 14.5 | 25.2 | 25.2 |
| exercise: binary search tree | 6.2 | 8.1 | 6.8 | 6.9 | 8.1 | 8.1 |
| exercise: object-oriented design | 5.6 | 8.5 | 6.1 | 5.9 | 8.5 | 8.5 |
| lecture: introduction to databases | 12.0 | 33.3 | 14.6 | 13.1 | 33.3 | 33.3 |
| lecture: machine learning basics | 6.3 | 8.7 | 7.1 | 7.1 | 8.7 | 8.7 |
| exam: midterm | 6.7 | 9.0 | 7.5 | 7.4 | 9.0 | 9.0 |
| exam: final | 6.6 | 9.0 | 7.7 | 7.4 | 9.0 | 9.0 |
| faq: submission deadline | 5.9 | 8.2 | 6.5 | 6.3 | 8.2 | 8.2 |
| faq: grading criteria | 6.2 | 7.8 | 7.2 | 7.2 | 7.8 | 7.8 |
| channel: general announcements | 5.7 | 10.0 | 6.6 | 6.3 | 10.0 | 10.0 |
| channel: organization | 5.7 | 8.2 | 6.7 | 6.5 | 8.2 | 8.2 |
| course: software engineering | 5.7 | 7.5 | 6.4 | 6.2 | 7.5 | 7.5 |
| course: computer science | 5.8 | 8.3 | 6.4 | 6.2 | 8.3 | 8.3 |
| message: help with exercise | 5.6 | 7.1 | 6.2 | 6.0 | 7.1 | 7.1 |
| message: clarification needed | 10.7 | 15.1 | 12.7 | 12.7 | 15.1 | 15.1 |

## Summary Statistics (All-Types Combined)

Aggregated across all benchmark queries for the `types=all` variant.

| Metric | Value (ms) |
|--------|----------:|
| Min mean latency | 8.9 |
| Max mean latency | 14.6 |
| Overall mean latency | 11.9 |
| Overall p95 (worst query p95) | 32.6 |
| Overall p99 (worst query p99) | 32.6 |

