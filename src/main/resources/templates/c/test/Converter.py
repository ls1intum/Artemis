#!/usr/bin/env python3
import sys, getopt
from xml.sax.saxutils import escape

def main(argv):
    inputfile = ''
    outputfile = ''
    try:
        opts, args = getopt.getopt(argv,"hi:o:",["ifile=","ofile="])
    except getopt.GetoptError:
        print('python3 Converter.py -i <inputfile> -o <outputfile>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print('python3 Converter.py -i <inputfile> -o <outputfile>')
            sys.exit()
        elif opt in ("-i", "--ifile"):
            inputfile = arg
        elif opt in ("-o", "--ofile"):
            outputfile = arg
    print('Input file is:', inputfile)
    print('Output file is:', outputfile)

    f = open(inputfile, "r")

    # Read whole file to a string
    data = f.read()

    f.close()

    xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
    rootOpening = "<root>\n"
    rootClosing = "</root>\n"

    # Escape text so it will not cause issues with XML
    escapedText = escape(data)

    out = open(outputfile, "w")
    out.write(xmlHeader + rootOpening + escapedText + rootClosing)
    out.close()

if __name__ == "__main__":
    main(sys.argv[1:])
