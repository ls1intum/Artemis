NAME=verzoegerung

all:
	ghdl -a $(NAME).vhd
	ghdl -a $(NAME)_tb.vhd
	ghdl -e $(NAME)_tb
	ghdl -r $(NAME)_tb

clean:
	rm *.o
