#!/usr/bin/env ruby
# frozen_string_literal: true

require "date"

require_relative "context"
require_relative "policy"

module Client
  ITERATIONS = 10
  DATES_LENGTH_MIN = 5
  DATES_LENGTH_MAX = 15

  # Main method.
  # Add code to demonstrate your implementation here.
  def self.main
    context = Context.new
    policy = Policy.new(context)

    ITERATIONS.times do
      dates = create_random_dates

      context.dates = dates
      policy.configure

      print("Unsorted Array of dates: ")
      print_dates(dates)

      context.sort

      print("Sorted Array of dates:   ")
      print_dates(dates)
    end
  end

  # Generates a List of random Date objects with random List size between
  # DATES_LENGTH_MIN and DATES_LENGTH_MAX.
  # @return [Array<Date>] an Array of random Date objects.
  def self.create_random_dates
    dates_length = Random.rand(DATES_LENGTH_MIN..DATES_LENGTH_MAX)

    lowest_date = Date.new(2024, 9, 15)
    highest_date = Date.new(2025, 1, 15)

    Array.new(dates_length) { random_date_within(lowest_date, highest_date) }
  end

  # Creates a random Date within the given range.
  # @param low [Date] the lower bound
  # @param high [Date] the upper bound
  # @return [Date] a random Date within the given range
  def self.random_date_within(low, high)
    random_jd = Random.rand(low.jd..high.jd)
    Date.jd(random_jd)
  end

  # Prints out the given Array of Date objects.
  # @param dates [Array<Date>] list of the dates to print
  def self.print_dates(dates)
    puts(dates.join(", "))
  end
end

Client.main
