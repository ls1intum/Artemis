# frozen_string_literal: true

require_relative "bubble_sort"
require_relative "merge_sort"

class Policy
  attr_accessor :context

  DATES_SIZE_THRESHOLD = 10

  def initialize(context)
    @context = context
  end

  # Chooses a strategy depending on the number of date objects.
  def configure
    sort_algorithm = if @context.dates.length > DATES_SIZE_THRESHOLD
                       MergeSort.new
                     else
                       BubbleSort.new
                     end
    @context.sort_algorithm = sort_algorithm
  end
end
