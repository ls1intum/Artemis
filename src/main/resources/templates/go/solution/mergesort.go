package ${packageName}

import "time"

type MergeSort struct{}

func NewMergeSort() *MergeSort {
	return new(MergeSort)
}

// PerformSort is a wrapper method for the real MergeSort algorithm.
func (m *MergeSort) PerformSort(input []time.Time) {
	mergeSort(input)
}

// mergeSort recursively applies the MergeSort algorithm.
func mergeSort(input []time.Time) {
	if len(input) < 2 {
		return
	}

	middle := len(input) / 2
	mergeSort(input[:middle])
	mergeSort(input[middle:])
	merge(input, middle)
}

// merge merges two ranges within input defined from low to mid and from mid+1 to high.
func merge(input []time.Time, mid int) {
	temp := make([]time.Time, len(input))

	left, right, k := 0, mid, 0

	for left < mid && right < len(input) {
		if input[left].Before(input[right]) {
			temp[k] = input[left]
			left++
		} else {
			temp[k] = input[right]
			right++
		}
		k++
	}

	for left < mid {
		temp[k] = input[left]
		left++
		k++
	}
	for right < len(input) {
		temp[k] = input[right]
		right++
		k++
	}

	copy(input, temp)
}
