package assignment

import "time"

type MergeSort struct{}

func NewMergeSort() *MergeSort {
	return new(MergeSort)
}

// PerformSort is a wrapper method for the real MergeSort algorithm.
func (m MergeSort) PerformSort(input []time.Time) {
	mergeSort(input, 0, len(input)-1)
}

// mergeSort recursively applies the MergeSort algorithm.
func mergeSort(input []time.Time, low, high int) {
	if high-low < 1 {
		return
	}
	mid := (low + high) / 2
	mergeSort(input, low, mid)
	mergeSort(input, mid+1, high)
	merge(input, low, mid, high)
}

// merge merges two ranges within input defined from low to mid and from mid+1 to high.
func merge(input []time.Time, low, mid, high int) {
	temp := make([]time.Time, high-low+1)
	left, right, k := low, mid+1, 0

	for left <= mid && right <= high {
		if input[left].Before(input[right]) || input[left].Equal(input[right]) {
			temp[k] = input[left]
			left++
		} else {
			temp[k] = input[right]
			right++
		}
		k++
	}

	for left <= mid {
		temp[k] = input[left]
		left++
		k++
	}
	for right <= high {
		temp[k] = input[right]
		right++
		k++
	}

	for i, val := range temp {
		input[low+i] = val
	}
}
