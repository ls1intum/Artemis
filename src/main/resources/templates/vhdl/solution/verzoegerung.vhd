library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity verzoegerung is
port(
        CLK, START : in std_logic;
        STOP : in std_logic;        -- Aufgabe 2 
        ALARM : out std_logic
    );
end entity;

architecture behaviour of verzoegerung is

signal z : unsigned(2 downto 0) := "000";

begin
    ALARM <= '1' when z = "100" else '0'; -- Ausgang
process(CLK)
begin

    if rising_edge(CLK) then
    
--        if STOP = '1' then      -- synchroner Reset (Aufgabe 2), für Afgabe 2 unkommentieren
--            z <= "000";
        
        else
            case z is
                when "000" =>       -- Startzustand
                                if START = '1' then 
                                    z <= "001";
                                end if;
                when "001" | "010" | "011" =>   -- Wartezustaende    
                                    z <= z + 1; 
                when "100" =>       -- Alarmzustand
                                if START = '0' then
                                    z <= "000";
                                end if;
                when others => 
            
            end case;   
--        end if;               -- für Aufgabe 2 unkommentieren 
    end if;

end process;

end architecture;
