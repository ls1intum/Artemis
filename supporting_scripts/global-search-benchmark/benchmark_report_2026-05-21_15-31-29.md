# Global Search Benchmark Report

**Generated:** 2026-05-21 15:31:29 UTC

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
| `exercise` | 166 |
| `lecture` | 120 |
| `lecture_unit` | 100 |
| `exam` | 1 |
| `faq` | 12 |
| `channel` | 192 |
| `course` | 11 |
| `post` | 2849 |
| `answer_post` | 1 |
| **Total** | **3452** |

## Benchmark Results — All Types Combined (`types=all`)

All latency values are in milliseconds (ms).

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 39.9 | 75.5 | 53.3 | 49.0 | 75.5 | 75.5 |
| generic: programming | 17.3 | 64.3 | 30.0 | 27.7 | 64.3 | 64.3 |
| generic: quiz | 13.1 | 16.5 | 14.2 | 14.2 | 16.5 | 16.5 |
| generic: lecture notes | 12.0 | 18.2 | 14.2 | 14.0 | 18.2 | 18.2 |
| generic: exam preparation | 15.4 | 33.5 | 21.3 | 19.6 | 33.5 | 33.5 |
| exercise: sorting algorithm | 13.2 | 19.7 | 15.8 | 15.3 | 19.7 | 19.7 |
| exercise: binary search tree | 12.9 | 20.1 | 15.5 | 15.2 | 20.1 | 20.1 |
| exercise: object-oriented design | 13.8 | 22.6 | 17.3 | 16.2 | 22.6 | 22.6 |
| lecture: introduction to databases | 22.0 | 121.5 | 51.4 | 40.4 | 121.5 | 121.5 |
| lecture: machine learning basics | 13.2 | 20.0 | 14.8 | 14.3 | 20.0 | 20.0 |
| exam: midterm | 13.2 | 25.6 | 16.5 | 15.3 | 25.6 | 25.6 |
| exam: final | 28.3 | 63.9 | 38.3 | 34.5 | 63.9 | 63.9 |
| faq: submission deadline | 14.1 | 21.3 | 15.9 | 15.1 | 21.3 | 21.3 |
| faq: grading criteria | 42.6 | 124.4 | 60.5 | 58.2 | 124.4 | 124.4 |
| channel: general announcements | 19.9 | 29.3 | 22.6 | 22.2 | 29.3 | 29.3 |
| channel: organization | 12.4 | 18.4 | 13.9 | 13.5 | 18.4 | 18.4 |
| course: software engineering | 16.7 | 27.7 | 20.7 | 19.9 | 27.7 | 27.7 |
| course: computer science | 15.4 | 40.8 | 21.6 | 19.4 | 40.8 | 40.8 |
| message: help with exercise | 17.7 | 100.6 | 34.5 | 28.8 | 100.6 | 100.6 |
| message: clarification needed | 14.0 | 38.9 | 17.4 | 16.0 | 38.9 | 38.9 |

## Benchmark Results — Per Entity Type

All latency values are in milliseconds (ms).

### `exercise`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 13.9 | 90.0 | 35.3 | 30.1 | 90.0 | 90.0 |
| generic: programming | 15.1 | 40.7 | 22.7 | 21.2 | 40.7 | 40.7 |
| generic: quiz | 11.4 | 20.9 | 14.1 | 13.4 | 20.9 | 20.9 |
| generic: lecture notes | 13.2 | 22.9 | 14.3 | 13.7 | 22.9 | 22.9 |
| generic: exam preparation | 12.4 | 18.4 | 15.3 | 14.8 | 18.4 | 18.4 |
| exercise: sorting algorithm | 12.8 | 17.5 | 14.6 | 14.5 | 17.5 | 17.5 |
| exercise: binary search tree | 14.1 | 21.4 | 16.0 | 15.8 | 21.4 | 21.4 |
| exercise: object-oriented design | 13.9 | 18.0 | 14.9 | 14.6 | 18.0 | 18.0 |
| lecture: introduction to databases | 32.1 | 167.0 | 62.4 | 44.0 | 167.0 | 167.0 |
| lecture: machine learning basics | 13.8 | 34.0 | 17.1 | 15.5 | 34.0 | 34.0 |
| exam: midterm | 14.4 | 28.3 | 17.3 | 16.7 | 28.3 | 28.3 |
| exam: final | 14.2 | 87.7 | 30.1 | 22.2 | 87.7 | 87.7 |
| faq: submission deadline | 15.7 | 94.4 | 32.9 | 29.0 | 94.4 | 94.4 |
| faq: grading criteria | 16.2 | 23.1 | 19.1 | 19.3 | 23.1 | 23.1 |
| channel: general announcements | 15.9 | 22.1 | 17.5 | 17.0 | 22.1 | 22.1 |
| channel: organization | 13.0 | 33.3 | 16.6 | 15.7 | 33.3 | 33.3 |
| course: software engineering | 16.2 | 129.3 | 32.3 | 21.1 | 129.3 | 129.3 |
| course: computer science | 12.9 | 21.2 | 15.3 | 14.4 | 21.2 | 21.2 |
| message: help with exercise | 15.0 | 44.9 | 22.5 | 19.6 | 44.9 | 44.9 |
| message: clarification needed | 12.6 | 23.5 | 17.3 | 16.9 | 23.5 | 23.5 |

### `lecture`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 13.2 | 35.9 | 18.0 | 15.4 | 35.9 | 35.9 |
| generic: programming | 17.6 | 107.0 | 33.5 | 27.2 | 107.0 | 107.0 |
| generic: quiz | 11.7 | 19.6 | 13.7 | 13.0 | 19.6 | 19.6 |
| generic: lecture notes | 13.5 | 49.7 | 18.9 | 16.8 | 49.7 | 49.7 |
| generic: exam preparation | 11.9 | 19.9 | 14.9 | 14.2 | 19.9 | 19.9 |
| exercise: sorting algorithm | 12.9 | 23.1 | 15.9 | 15.3 | 23.1 | 23.1 |
| exercise: binary search tree | 12.6 | 19.1 | 14.7 | 14.0 | 19.1 | 19.1 |
| exercise: object-oriented design | 12.8 | 22.4 | 16.5 | 15.7 | 22.4 | 22.4 |
| lecture: introduction to databases | 17.7 | 37.8 | 25.3 | 24.6 | 37.8 | 37.8 |
| lecture: machine learning basics | 13.7 | 53.8 | 24.3 | 20.9 | 53.8 | 53.8 |
| exam: midterm | 13.8 | 18.7 | 15.6 | 15.5 | 18.7 | 18.7 |
| exam: final | 14.8 | 53.6 | 25.0 | 20.2 | 53.6 | 53.6 |
| faq: submission deadline | 14.0 | 75.0 | 26.7 | 16.2 | 75.0 | 75.0 |
| faq: grading criteria | 14.3 | 27.3 | 18.9 | 18.3 | 27.3 | 27.3 |
| channel: general announcements | 15.1 | 21.5 | 17.2 | 17.1 | 21.5 | 21.5 |
| channel: organization | 12.6 | 19.9 | 15.2 | 14.6 | 19.9 | 19.9 |
| course: software engineering | 35.2 | 157.5 | 73.1 | 62.0 | 157.5 | 157.5 |
| course: computer science | 12.7 | 80.5 | 33.7 | 20.2 | 80.5 | 80.5 |
| message: help with exercise | 22.0 | 116.6 | 43.0 | 34.6 | 116.6 | 116.6 |
| message: clarification needed | 12.4 | 36.7 | 18.4 | 15.5 | 36.7 | 36.7 |

### `lecture_unit`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 11.6 | 55.5 | 18.6 | 14.5 | 55.5 | 55.5 |
| generic: programming | 15.7 | 42.9 | 25.1 | 23.7 | 42.9 | 42.9 |
| generic: quiz | 11.4 | 14.1 | 12.6 | 12.5 | 14.1 | 14.1 |
| generic: lecture notes | 12.3 | 16.0 | 13.7 | 13.4 | 16.0 | 16.0 |
| generic: exam preparation | 10.9 | 15.4 | 12.2 | 12.1 | 15.4 | 15.4 |
| exercise: sorting algorithm | 12.7 | 44.5 | 18.9 | 16.4 | 44.5 | 44.5 |
| exercise: binary search tree | 12.6 | 21.1 | 14.1 | 13.8 | 21.1 | 21.1 |
| exercise: object-oriented design | 14.6 | 64.4 | 23.4 | 18.5 | 64.4 | 64.4 |
| lecture: introduction to databases | 13.3 | 35.3 | 16.7 | 15.2 | 35.3 | 35.3 |
| lecture: machine learning basics | 16.3 | 134.8 | 47.1 | 34.2 | 134.8 | 134.8 |
| exam: midterm | 13.2 | 19.9 | 15.1 | 14.3 | 19.9 | 19.9 |
| exam: final | 14.1 | 45.1 | 24.4 | 19.4 | 45.1 | 45.1 |
| faq: submission deadline | 15.0 | 150.9 | 55.1 | 31.0 | 150.9 | 150.9 |
| faq: grading criteria | 14.0 | 23.1 | 18.0 | 17.5 | 23.1 | 23.1 |
| channel: general announcements | 14.0 | 31.1 | 17.6 | 15.3 | 31.1 | 31.1 |
| channel: organization | 12.6 | 47.2 | 22.5 | 21.5 | 47.2 | 47.2 |
| course: software engineering | 15.2 | 54.0 | 25.0 | 20.2 | 54.0 | 54.0 |
| course: computer science | 13.1 | 17.8 | 15.4 | 15.5 | 17.8 | 17.8 |
| message: help with exercise | 17.5 | 208.7 | 78.4 | 56.4 | 208.7 | 208.7 |
| message: clarification needed | 12.7 | 53.8 | 18.7 | 15.1 | 53.8 | 53.8 |

### `exam`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 12.3 | 70.0 | 22.8 | 19.1 | 70.0 | 70.0 |
| generic: programming | 14.3 | 36.2 | 23.3 | 21.2 | 36.2 | 36.2 |
| generic: quiz | 7.5 | 11.0 | 8.7 | 8.7 | 11.0 | 11.0 |
| generic: lecture notes | 7.7 | 10.1 | 9.0 | 9.1 | 10.1 | 10.1 |
| generic: exam preparation | 10.0 | 18.9 | 13.3 | 13.1 | 18.9 | 18.9 |
| exercise: sorting algorithm | 13.7 | 59.1 | 19.9 | 16.4 | 59.1 | 59.1 |
| exercise: binary search tree | 11.8 | 14.9 | 13.2 | 12.9 | 14.9 | 14.9 |
| exercise: object-oriented design | 8.6 | 19.0 | 11.3 | 10.7 | 19.0 | 19.0 |
| lecture: introduction to databases | 8.9 | 18.0 | 10.4 | 9.9 | 18.0 | 18.0 |
| lecture: machine learning basics | 15.6 | 95.0 | 32.3 | 21.9 | 95.0 | 95.0 |
| exam: midterm | 8.1 | 10.5 | 9.4 | 9.4 | 10.5 | 10.5 |
| exam: final | 12.7 | 59.6 | 25.9 | 17.0 | 59.6 | 59.6 |
| faq: submission deadline | 14.2 | 35.4 | 20.8 | 20.5 | 35.4 | 35.4 |
| faq: grading criteria | 12.3 | 24.7 | 16.5 | 15.2 | 24.7 | 24.7 |
| channel: general announcements | 9.7 | 67.4 | 21.6 | 16.5 | 67.4 | 67.4 |
| channel: organization | 12.1 | 40.2 | 18.0 | 14.6 | 40.2 | 40.2 |
| course: software engineering | 13.3 | 21.0 | 16.0 | 15.1 | 21.0 | 21.0 |
| course: computer science | 9.7 | 25.5 | 13.5 | 12.8 | 25.5 | 25.5 |
| message: help with exercise | 11.0 | 165.5 | 34.1 | 24.1 | 165.5 | 165.5 |
| message: clarification needed | 13.0 | 36.7 | 17.6 | 15.0 | 36.7 | 36.7 |

### `faq`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 13.0 | 39.9 | 17.9 | 15.6 | 39.9 | 39.9 |
| generic: programming | 11.8 | 24.3 | 18.8 | 18.7 | 24.3 | 24.3 |
| generic: quiz | 10.5 | 13.6 | 12.1 | 12.1 | 13.6 | 13.6 |
| generic: lecture notes | 10.9 | 18.6 | 12.8 | 12.4 | 18.6 | 18.6 |
| generic: exam preparation | 10.3 | 26.9 | 13.9 | 12.7 | 26.9 | 26.9 |
| exercise: sorting algorithm | 12.9 | 41.3 | 18.0 | 17.0 | 41.3 | 41.3 |
| exercise: binary search tree | 12.8 | 16.2 | 13.9 | 13.6 | 16.2 | 16.2 |
| exercise: object-oriented design | 13.2 | 82.1 | 24.2 | 15.9 | 82.1 | 82.1 |
| lecture: introduction to databases | 12.9 | 31.9 | 18.6 | 16.0 | 31.9 | 31.9 |
| lecture: machine learning basics | 14.1 | 116.1 | 66.9 | 69.2 | 116.1 | 116.1 |
| exam: midterm | 11.4 | 14.6 | 12.8 | 12.7 | 14.6 | 14.6 |
| exam: final | 13.4 | 119.9 | 28.1 | 21.4 | 119.9 | 119.9 |
| faq: submission deadline | 14.1 | 23.7 | 16.5 | 15.8 | 23.7 | 23.7 |
| faq: grading criteria | 11.6 | 16.8 | 13.4 | 13.0 | 16.8 | 16.8 |
| channel: general announcements | 15.0 | 32.6 | 19.9 | 18.8 | 32.6 | 32.6 |
| channel: organization | 15.1 | 46.9 | 22.8 | 21.0 | 46.9 | 46.9 |
| course: software engineering | 13.3 | 25.2 | 18.8 | 18.9 | 25.2 | 25.2 |
| course: computer science | 11.3 | 16.3 | 12.8 | 12.5 | 16.3 | 16.3 |
| message: help with exercise | 16.2 | 43.3 | 23.1 | 19.9 | 43.3 | 43.3 |
| message: clarification needed | 12.4 | 30.4 | 15.4 | 14.3 | 30.4 | 30.4 |

### `channel`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 12.5 | 90.7 | 31.0 | 22.7 | 90.7 | 90.7 |
| generic: programming | 12.1 | 21.4 | 16.0 | 15.7 | 21.4 | 21.4 |
| generic: quiz | 10.4 | 13.4 | 11.5 | 11.2 | 13.4 | 13.4 |
| generic: lecture notes | 12.4 | 16.9 | 14.0 | 13.6 | 16.9 | 16.9 |
| generic: exam preparation | 11.2 | 20.2 | 15.2 | 14.7 | 20.2 | 20.2 |
| exercise: sorting algorithm | 11.8 | 21.3 | 14.6 | 14.2 | 21.3 | 21.3 |
| exercise: binary search tree | 13.0 | 14.8 | 14.0 | 14.1 | 14.8 | 14.8 |
| exercise: object-oriented design | 15.1 | 134.3 | 48.2 | 36.4 | 134.3 | 134.3 |
| lecture: introduction to databases | 15.6 | 44.3 | 29.1 | 31.8 | 44.3 | 44.3 |
| lecture: machine learning basics | 13.4 | 32.0 | 16.6 | 15.3 | 32.0 | 32.0 |
| exam: midterm | 11.3 | 20.9 | 14.2 | 13.7 | 20.9 | 20.9 |
| exam: final | 12.5 | 82.7 | 29.2 | 17.2 | 82.7 | 82.7 |
| faq: submission deadline | 14.3 | 24.0 | 15.8 | 15.1 | 24.0 | 24.0 |
| faq: grading criteria | 11.5 | 21.2 | 15.1 | 13.7 | 21.2 | 21.2 |
| channel: general announcements | 17.2 | 67.9 | 30.0 | 28.5 | 67.9 | 67.9 |
| channel: organization | 13.2 | 25.0 | 17.7 | 16.0 | 25.0 | 25.0 |
| course: software engineering | 15.4 | 61.3 | 22.8 | 18.5 | 61.3 | 61.3 |
| course: computer science | 12.3 | 33.5 | 15.4 | 13.8 | 33.5 | 33.5 |
| message: help with exercise | 11.3 | 22.7 | 16.3 | 15.9 | 22.7 | 22.7 |
| message: clarification needed | 12.8 | 44.8 | 20.4 | 16.4 | 44.8 | 44.8 |

### `course`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 10.9 | 37.7 | 16.9 | 14.6 | 37.7 | 37.7 |
| generic: programming | 13.3 | 20.8 | 15.3 | 14.9 | 20.8 | 20.8 |
| generic: quiz | 10.4 | 15.3 | 11.3 | 11.0 | 15.3 | 15.3 |
| generic: lecture notes | 11.5 | 15.5 | 12.9 | 12.7 | 15.5 | 15.5 |
| generic: exam preparation | 15.1 | 37.4 | 21.3 | 18.6 | 37.4 | 37.4 |
| exercise: sorting algorithm | 12.6 | 16.6 | 14.1 | 13.9 | 16.6 | 16.6 |
| exercise: binary search tree | 12.2 | 32.7 | 15.2 | 14.2 | 32.7 | 32.7 |
| exercise: object-oriented design | 13.5 | 73.6 | 34.5 | 32.6 | 73.6 | 73.6 |
| lecture: introduction to databases | 13.6 | 120.5 | 44.2 | 37.6 | 120.5 | 120.5 |
| lecture: machine learning basics | 13.8 | 32.0 | 19.9 | 18.0 | 32.0 | 32.0 |
| exam: midterm | 11.8 | 36.4 | 16.9 | 14.4 | 36.4 | 36.4 |
| exam: final | 10.9 | 14.3 | 12.6 | 12.6 | 14.3 | 14.3 |
| faq: submission deadline | 13.6 | 31.2 | 15.3 | 14.5 | 31.2 | 31.2 |
| faq: grading criteria | 14.7 | 116.3 | 40.0 | 31.2 | 116.3 | 116.3 |
| channel: general announcements | 14.0 | 18.7 | 16.1 | 15.8 | 18.7 | 18.7 |
| channel: organization | 14.6 | 27.9 | 18.9 | 17.7 | 27.9 | 27.9 |
| course: software engineering | 14.7 | 36.0 | 21.0 | 17.7 | 36.0 | 36.0 |
| course: computer science | 11.9 | 22.1 | 15.0 | 14.0 | 22.1 | 22.1 |
| message: help with exercise | 11.7 | 15.7 | 13.6 | 14.2 | 15.7 | 15.7 |
| message: clarification needed | 20.6 | 53.6 | 29.0 | 26.4 | 53.6 | 53.6 |

### `post`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 22.6 | 239.9 | 90.2 | 52.1 | 239.9 | 239.9 |
| generic: programming | 45.4 | 72.2 | 51.8 | 50.8 | 72.2 | 72.2 |
| generic: quiz | 40.5 | 48.4 | 43.1 | 42.6 | 48.4 | 48.4 |
| generic: lecture notes | 39.7 | 48.7 | 44.1 | 44.2 | 48.7 | 48.7 |
| generic: exam preparation | 42.3 | 103.0 | 60.9 | 59.4 | 103.0 | 103.0 |
| exercise: sorting algorithm | 46.9 | 76.2 | 56.7 | 53.2 | 76.2 | 76.2 |
| exercise: binary search tree | 40.7 | 202.1 | 79.8 | 57.8 | 202.1 | 202.1 |
| exercise: object-oriented design | 44.6 | 235.0 | 108.2 | 99.6 | 235.0 | 235.0 |
| lecture: introduction to databases | 45.0 | 312.4 | 99.4 | 82.3 | 312.4 | 312.4 |
| lecture: machine learning basics | 39.1 | 95.0 | 62.2 | 62.5 | 95.0 | 95.0 |
| exam: midterm | 46.9 | 208.6 | 84.5 | 62.8 | 208.6 | 208.6 |
| exam: final | 36.4 | 57.0 | 41.5 | 40.4 | 57.0 | 57.0 |
| faq: submission deadline | 40.1 | 66.1 | 49.3 | 47.4 | 66.1 | 66.1 |
| faq: grading criteria | 56.9 | 337.3 | 142.6 | 145.1 | 337.3 | 337.3 |
| channel: general announcements | 36.6 | 63.5 | 44.5 | 41.5 | 63.5 | 63.5 |
| channel: organization | 43.5 | 58.7 | 48.0 | 46.1 | 58.7 | 58.7 |
| course: software engineering | 56.0 | 573.7 | 210.1 | 174.5 | 573.7 | 573.7 |
| course: computer science | 37.2 | 317.6 | 89.3 | 61.2 | 317.6 | 317.6 |
| message: help with exercise | 43.4 | 96.8 | 61.9 | 61.6 | 96.8 | 96.8 |
| message: clarification needed | 59.2 | 165.5 | 91.0 | 84.5 | 165.5 | 165.5 |

### `answer_post`

| Query | Min | Max | Mean | Median | p95 | p99 |
|-------|----:|----:|-----:|-------:|----:|----:|
| empty (browse mode) | 15.5 | 164.4 | 32.2 | 21.8 | 164.4 | 164.4 |
| generic: programming | 9.1 | 18.2 | 11.6 | 10.4 | 18.2 | 18.2 |
| generic: quiz | 7.3 | 12.7 | 9.5 | 9.6 | 12.7 | 12.7 |
| generic: lecture notes | 8.1 | 13.2 | 10.2 | 10.2 | 13.2 | 13.2 |
| generic: exam preparation | 9.3 | 14.4 | 10.6 | 10.3 | 14.4 | 14.4 |
| exercise: sorting algorithm | 15.9 | 22.9 | 17.8 | 17.0 | 22.9 | 22.9 |
| exercise: binary search tree | 9.5 | 21.3 | 11.7 | 10.3 | 21.3 | 21.3 |
| exercise: object-oriented design | 14.1 | 62.7 | 26.3 | 23.8 | 62.7 | 62.7 |
| lecture: introduction to databases | 13.6 | 28.3 | 17.6 | 15.9 | 28.3 | 28.3 |
| lecture: machine learning basics | 9.8 | 26.5 | 12.3 | 10.9 | 26.5 | 26.5 |
| exam: midterm | 9.8 | 72.8 | 26.9 | 23.2 | 72.8 | 72.8 |
| exam: final | 8.8 | 40.1 | 14.4 | 11.6 | 40.1 | 40.1 |
| faq: submission deadline | 8.1 | 17.9 | 10.8 | 9.9 | 17.9 | 17.9 |
| faq: grading criteria | 10.2 | 15.3 | 12.3 | 12.1 | 15.3 | 15.3 |
| channel: general announcements | 8.7 | 13.0 | 10.7 | 10.8 | 13.0 | 13.0 |
| channel: organization | 9.0 | 19.7 | 11.2 | 10.5 | 19.7 | 19.7 |
| course: software engineering | 10.5 | 23.3 | 13.2 | 12.4 | 23.3 | 23.3 |
| course: computer science | 10.0 | 66.7 | 22.1 | 17.4 | 66.7 | 66.7 |
| message: help with exercise | 9.0 | 13.9 | 11.1 | 10.8 | 13.9 | 13.9 |
| message: clarification needed | 16.9 | 27.3 | 20.3 | 19.9 | 27.3 | 27.3 |

## Summary Statistics (All-Types Combined)

Aggregated across all benchmark queries for the `types=all` variant.

| Metric | Value (ms) |
|--------|----------:|
| Min mean latency | 13.9 |
| Max mean latency | 60.5 |
| Overall mean latency | 25.5 |
| Overall p95 (worst query p95) | 124.4 |
| Overall p99 (worst query p99) | 124.4 |

