#!/usr/bin/env python3
import getopt
import sys
from xml.sax.saxutils import escape


def usage():
    print("python3 <script> -i <inputfile> -o <outputfile>")


def main(argv):
    inputfile = ""
    outputfile = ""
    try:
        opts, args = getopt.getopt(argv, "hi:o:", ["ifile=", "ofile="])
    except getopt.GetoptError as err:
        print(err)
        usage()
        sys.exit(2)
    for opt, arg in opts:
        if opt == "-h":
            usage()
        elif opt in ("-i", "--ifile"):
            inputfile = arg
        elif opt in ("-o", "--ofile"):
            outputfile = arg

    print("Input file is:", inputfile)
    print("Output file is:", outputfile)

    with open(inputfile, "r") as file:

        # Read whole file to a string
        data = file.read()

    xml_header = '<?xml version="1.0" encoding="UTF-8" ?>\n'
    root_opening = "<root>\n"
    root_closing = "</root>\n"

    # Escape text so it will not cause issues with XML
    escaped_text = escape(data)

    with open(outputfile, "w") as file:
        file.write(xml_header + root_opening + escaped_text + root_closing)


if __name__ == "__main__":
    main(sys.argv[1:])
