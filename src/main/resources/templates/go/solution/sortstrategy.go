package assignment

import "time"

type SortStrategy interface {
	PerformSort(input []time.Time)
}
