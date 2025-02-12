# frozen_string_literal: true

class MergeSort
  # Wrapper method for the real MergeSort algorithm.
  # @param input [Array<Date>] the Array of Dates to be sorted
  def perform_sort(input)
    mergesort(input, 0, input.length)
  end

  private

  # Recursive merge sort method
  def mergesort(input, low, high)
    return if high - low <= 1

    mid = (low + high) / 2

    mergesort(input, low, mid)
    mergesort(input, mid, high)
    merge(input, low, mid, high)
  end

  # Merge method
  def merge(input, low, middle, high)
    temp = Array.new(high - low)

    left_index = low
    right_index = middle
    whole_index = 0

    while left_index < middle && right_index < high
      if input[left_index] <= input[right_index]
        temp[whole_index] = input[left_index]
        left_index += 1
      else
        temp[whole_index] = input[right_index]
        right_index += 1
      end
      whole_index += 1
    end

    while left_index < middle
      temp[whole_index] = input[left_index]
      left_index += 1
      whole_index += 1
    end

    while right_index < high
      temp[whole_index] = input[right_index]
      right_index += 1
      whole_index += 1
    end

    temp.each_with_index do |value, index|
      input[low + index] = value
    end
  end
end
