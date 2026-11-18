# frozen_string_literal: true

class BubbleSort
  # Sorts dates with BubbleSort.
  # @param input [Array<Date>] the Array of Dates to be sorted
  def perform_sort(input)
    (input.length - 1).downto(0) do |i|
      (0...i).each do |j|
        input[j], input[j + 1] = input[j + 1], input[j] if input[j] > input[j + 1]
      end
    end
  end
end
