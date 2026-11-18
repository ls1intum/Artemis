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

   uut: verzoegerung port map (START => START, STOP => STOP, CLK => CLK,
          			ALARM => ALARM
        );

  p0 :process
  begin
	  CLK <= '0';
	  wait for clk_period/2;
	  CLK <= '1';
	  wait for clk_period/2;
  end process;

  p1: process
  begin  

          wait for 2 * clk_period;
	  START <= '1';
	  wait for clk_period;
	  
          wait for clk_period;
	  
          wait for clk_period;
	  
          wait for clk_period;
	  
          wait for clk_period;
          
          wait for clk_period;
	  
	  
	  START <= '0';
          
          wait for clk_period;

          START <= '1';
	  
          wait for clk_period;

          wait for clk_period;
	  
          STOP <= '1';
           
          wait for clk_period;

          wait for clk_period;
 
          wait for clk_period;
 
          wait for clk_period;



  end process;


end;
