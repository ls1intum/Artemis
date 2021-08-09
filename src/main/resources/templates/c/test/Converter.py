#!/usr/bin/env python3
import sys, getopt

from json2xml import json2xml
from json2xml.utils import readfromjson, readfromstring

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
    raw = f.readlines()
    jsonInput = raw[0]
    data = readfromstring(jsonInput)
    convertedData = json2xml.Json2xml(data).to_xml()
    out = open(outputfile, "w")
    out.write(convertedData)

if __name__ == "__main__":
    main(sys.argv[1:])
