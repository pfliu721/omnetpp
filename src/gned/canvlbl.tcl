#==========================================================================
#  CANVLBL.TCL -
#            part of the GNED, the Tcl/Tk graphical topology editor of
#                            OMNeT++
#
#   Contains: text editing on canvas
#
#   By Andras Varga
#
#==========================================================================

#----------------------------------------------------------------#
#  Copyright (C) 1992,99 Andras Varga
#  Technical University of Budapest, Dept. of Telecommunications,
#  Stoczek u.2, H-1111 Budapest, Hungary.
#
#  This file is distributed WITHOUT ANY WARRANTY. See the file
#  `license' for details on this and other legal matters.
#----------------------------------------------------------------#


# editCanvasLabel --
#
# Start editing a text item on a canvas. Uses an entry widget.
#  c    canvas
#  id   tag or id of text item
#  cmd  command to eval (with the edited string as extra arg)
#       when editing is committed. Canvas text item will be set to
#       the return value of the proc.
#
proc editCanvasLabel {c id cmd} {
    global tmp

    set id [$c find withtag $id]
    set xy [$c coords $id]
    set txt  [$c itemcget $id -text]
    set font [$c itemcget $id -font]
    set anch [$c itemcget $id -anchor]
    set tmp(canvasentry_orig) $txt
    $c itemconfig $id -text ""
    catch {destroy $c.e}
    entry $c.e -bd 1 -relief solid -font $font
    $c.e insert end $txt
    $c.e select range 0 end
    set eid [$c create window [lindex $xy 0] [expr [lindex $xy 1]-1] -window $c.e -anchor $anch]
    resizeCanvasEntry $c.e
    bind $c.e <FocusOut> [list commitCanvasEntry $c $id $c.e $eid $cmd]
    bind $c.e <Return> [list commitCanvasEntry $c $id $c.e $eid $cmd]
    bind $c.e <Escape> "cancelCanvasEntry $c $id $c.e $eid"
    bind $c.e <KeyPress> "after idle \"resizeCanvasEntry $c.e\""
    focus $c.e
}


# resizeCanvasEntry --
#
# Private proc for editCanvasLabel
#
proc resizeCanvasEntry {w} {
    set txt [$w get]
    $w config -width [expr [string length $txt]+2]
}


# commitCanvasEntry --
#
# Private proc for editCanvasLabel
#
proc commitCanvasEntry {c id e eid cmd} {
    # let $cmd modify the text from the entry
    set txt [eval $cmd [list [$e get]]]
    $c itemconfig $id -text $txt
    $c delete $eid
    destroy $e
}


# cancelCanvasEntry --
#
# Private proc for editCanvasLabel
#
proc cancelCanvasEntry {c id e eid} {
    global tmp
    $c itemconfig $id -text $tmp(canvasentry_orig)
    $c delete $eid
    destroy $e
}

