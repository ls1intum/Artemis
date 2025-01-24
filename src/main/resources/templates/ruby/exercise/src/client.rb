#!/usr/bin/env ruby
# frozen_string_literal: true

require "date"
require "English"

module Client
  # TODO: Implement BubbleSort
  # TODO: Implement MergeSort

  # TODO: Create and implement a Context class according to the UML class diagram
  # TODO: Create and implement a Policy class as described in the problem statement

  ITERATIONS = 10
  DATES_LENGTH_MIN = 5
  DATES_LENGTH_MAX = 15

  def self.main
    # TODO: Init Context and Policy

    # Run multiple times to simulate different sorting strategies
    ITERATIONS.times do
      dates = create_random_dates

      # TODO: Configure context

      print("Unsorted Array of dates: ")
      print_dates(dates)

      # TODO: Sort dates

      print("Sorted Array of dates:   ")
      print_dates(dates)
    end
  end

  def self.create_random_dates
    dates_length = Random.rand(DATES_LENGTH_MIN..DATES_LENGTH_MAX)

    lowest_date = Date.new(2024, 9, 15)
    highest_date = Date.new(2025, 1, 15)

    Array.new(dates_length) { random_date_within(lowest_date, highest_date) }
  end

  def self.random_date_within(low, high)
    random_jd = Random.rand(low.jd..high.jd)
    Date.jd(random_jd)
  end

  def self.print_dates(dates)
    $OUTPUT_RECORD_SEPARATOR = "\n"
    $OUTPUT_FIELD_SEPARATOR = ", "
    print(*dates)
    $OUTPUT_RECORD_SEPARATOR = nil
    $OUTPUT_FIELD_SEPARATOR = nil
  end
end

Client.main
