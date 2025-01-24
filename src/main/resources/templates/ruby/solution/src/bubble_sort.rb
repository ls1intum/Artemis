# frozen_string_literal: true

class BubbleSort
  def perform_sort(input)
    (input.length - 1).downto(0) do |i|
      (0...i).each do |j|
        input[j], input[j + 1] = input[j + 1], input[j] if input[j] > input[j + 1]
      end
    end
  end
end
