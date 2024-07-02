library ieee;
use ieee.std_logic_1164.all;

-- This is an example testbench. To create Testcases that will be visible in the student's view, you need
-- to print to the console using report. report with severity level note will create a passed testcase, report
-- with severity level error will create a failed testcase.
-- The report message should also contain the testcase name and message separated by a dash.
-- Example: report "Testcase 1 - Test failed" severity error;

entity verzoegerung_tb is
end verzoegerung_tb;

architecture behavior of verzoegerung_tb is

    component verzoegerung
    port(
       CLK, START : in std_logic;
       STOP : in std_logic;        -- Aufgabe 2
       ALARM : out std_logic
    );
    end component;

   signal START : std_logic := '0';
   signal STOP : std_logic := '0';
   signal CLK : std_logic := '0';
   signal ALARM : std_logic;

   constant clk_period : time := 1 sec;

begin

   uut: verzoegerung port map (
       START => START,
       STOP => STOP,
       CLK => CLK,
       ALARM => ALARM
   );

   -- Clock generation process
   p0 : process
   begin
       CLK <= '0';
       wait for clk_period / 2;
       CLK <= '1';
       wait for clk_period / 2;
   end process;

   -- Stimulus process
   p1: process
   begin
       wait for 2 * clk_period;
       START <= '1';

       -- Assertions after START signal is set to '1'
       wait for clk_period;
       if (ALARM = '1') then
           report "Test1 - ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "Test1 - ALARM is '0' after 1 clock cycle" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "Test2 - ALARM should be '0' after 2 clock cycles" severity error;
       else
           report "Test2 - ALARM is '0' after 2 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "Test3 - ALARM should be '0' after 3 clock cycles" severity error;
       else
           report "Test3 - ALARM is '0' after 3 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '0') then
           report "Test4 - ALARM should be '1' after 4 clock cycles" severity error;
       else
           report "Test4 - ALARM is '1' after 4 clock cycles" severity note;
       end if;

       START <= '0';
       wait for clk_period;
       if (ALARM = '1') then
           report "Test5 - ALARM should be '1' after START is deasserted" severity error;
       else
           report "Test5 - ALARM is '0' after START is deasserted" severity note;
       end if;

       -- Test Case 2: Apply START again to check reset of alarm
       wait for clk_period;
       START <= '1';

       wait for clk_period;
       if (ALARM = '1') then
           report "Test6 - ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "Test6 - ALARM is '0' after 1 clock cycle" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "Test7 - ALARM should be '0' after 2 clock cycles" severity error;
       else
           report "Test7 - ALARM is '0' after 2 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "Test8 - ALARM should be '0' after 3 clock cycles" severity error;
       else
           report "Test8 - ALARM is '0' after 3 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '0') then
           report "Test9 - ALARM should be '1' after 4 clock cycles" severity error;
       else
           report "Test9 - ALARM is '1' after 4 clock cycles" severity note;
       end if;

       wait for clk_period;
       STOP <= '1';

       -- Assertions for STOP signal
       wait for clk_period;
       if (ALARM = '1') then
           report "Test10 - ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "Test10 - ALARM is '0' after 1 clock cycle" severity note;
       end if;

       -- Wait for a few clock cycles to observe the behavior after STOP is deasserted
       wait for 4 * clk_period;
       STOP <= '0';
       START <= '0';

       -- Finish simulation
       wait;
   end process;

end behavior;
