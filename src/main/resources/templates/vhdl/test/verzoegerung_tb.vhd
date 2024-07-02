library ieee;
use ieee.std_logic_1164.all;

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
           report "ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "ALARM is '0' after 1 clock cycle" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 2 clock cycles" severity error;
       else
           report "ALARM is '0' after 2 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 3 clock cycles" severity error;
       else
           report "ALARM is '0' after 3 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '0') then
           report "ALARM should be '1' after 4 clock cycles" severity error;
       else
           report "ALARM is '1' after 4 clock cycles" severity note;
       end if;

       START <= '0';
       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '1' after START is deasserted" severity error;
       else
           report "ALARM is '0' after START is deasserted" severity note;
       end if;

       -- Test Case 2: Apply START again to check reset of alarm
       wait for clk_period;
       START <= '1';

       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "ALARM is '0' after 1 clock cycle" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 2 clock cycles" severity error;
       else
           report "ALARM is '0' after 2 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 3 clock cycles" severity error;
       else
           report "ALARM is '0' after 3 clock cycles" severity note;
       end if;

       wait for clk_period;
       if (ALARM = '0') then
           report "ALARM should be '1' after 4 clock cycles" severity error;
       else
           report "ALARM is '1' after 4 clock cycles" severity note;
       end if;

       wait for clk_period;
       STOP <= '1';

       -- Assertions for STOP signal
       wait for clk_period;
       if (ALARM = '1') then
           report "ALARM should be '0' after 1 clock cycle" severity error;
       else
           report "ALARM is '0' after 1 clock cycle" severity note;
       end if;

       -- Wait for a few clock cycles to observe the behavior after STOP is deasserted
       wait for 4 * clk_period;
       STOP <= '0';
       START <= '0';

       -- Finish simulation
       wait;
   end process;

end behavior;
