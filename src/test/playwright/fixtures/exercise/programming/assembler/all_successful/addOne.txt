;%include "io.inc" <-- Uncomment for usage in SASM

;-------Do not modify this section-------
section .text
global CMAIN
CMAIN:
;-----------------------------------------
    ;add one to the number supplied in edi
    inc edi
    ret
