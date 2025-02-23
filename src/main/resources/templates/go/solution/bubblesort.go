package ${packageName}

import "time"

type BubbleSort struct{}

func NewBubbleSort() *BubbleSort {
	return new(BubbleSort)
}

func (b *BubbleSort) PerformSort(input []time.Time) {
	for i := len(input) - 1; i >= 0; i-- {
		for j := 0; j < i; j++ {
			if input[j].After(input[j+1]) {
				input[j], input[j+1] = input[j+1], input[j]
			}
		}
	}
}
