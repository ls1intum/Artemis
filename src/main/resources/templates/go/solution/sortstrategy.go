package assignment

import "time"

type SortStrategy interface {
	// PerformSort sorts a slice of dates.
	PerformSort(input []time.Time)
}
