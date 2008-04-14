
$listfname = $ARGV[0];
open(LISTFILE, $listfname) || die "cannot open $listfname";
while (<LISTFILE>)
{
    chomp;
    s/\r$//; # cygwin/mingw perl does not do CR/LF translation

    $fname = $_;

    if ($fname =~ /_m\./) {
        print "skipping $fname...\n";
        next;
    }

    print "processing $fname...\n";

    open(INFILE, $fname) || die "cannot open $fname";
    read(INFILE, $txt, 1000000) || die "cannot read $fname";
    close INFILE;

    # process $txt:

    $txt =~ s/cPolymorphic *\*dup\(\) *const *\{ *return +new +([_A-Za-z0-9]+)/\1 *dup() const {return new \1/mg;
    $txt =~ s/cObject *\*dup\(\) *const *\{ *return +new +([_A-Za-z0-9]+)/\1 *dup() const {return new \1/mg;
    $txt =~ s/\bcPar( +[A-Za-z])/cMsgPar\1/mg;   # message params are no longer cPar but cMsgPar
    $txt =~ s/\bnew +cPar\b/new cMsgPar/mg;

    # rename cPar methods
    $txt =~ s/\bsetFromText\b/parse/mg;
    $txt =~ s/\bgetAsText\b/str/mg;

    $txt =~ s/\bcException\b/cRuntimeError/mg;
    $txt =~ s/\bcSimpleChannel\b/cBasicChannel/mg;

    # message
    #FIXME TODO: if it sees: m->setLength(par("msgLength")) then it should insert a cast to long! otherwise "cannot convert cPar to int64"!

    # cModule
    $txt =~ s/\bgate\(([^,)]+)\)->size\(\)/gateSize(\1)/mg; # ???

    # rename cQueue methods
    $txt =~ s/\bgetTail *\( *\)/pop()/mg;
    $txt =~ s/\btail *\( *\)/front()/mg;

    # cStatistic methods
    $txt =~ s/\bsamples *\( *\)/count()/mg;
    $txt =~ s/\btransformed *\( *\)/isTransformed()/mg;

    # display string
    $txt =~ s/\bgetString *\( *\)/str()/mg;

     # cSimulation
    $txt =~ s/\brunningModule *\( *\)/activityModule()/mg;

    # cGate
    $txt =~ s/\brouteContains\b/pathContains/mg;
    $txt =~ s/\bisRouteOK *\( *\)/isPathOK()/mg;
    $txt =~ s/->datarate\(\)->doubleValue\(\)/->channel()->par("datarate").doubleValue()/mg;
    $txt =~ s/->delay\(\)->doubleValue\(\)/->channel()->par("delay").doubleValue()/mg;
    $txt =~ s/->error\(\)->doubleValue\(\)/->channel()->par("error").doubleValue()/mg;

    # print warnings
    $lineno = 0;
    foreach $line (split ("\n", $txt)) {
       $lineno++;
       # cObject
       if ($line =~ /\bcObject\b/) {
          print "*** warning at $fname:$lineno: cObject got renamed to cOwnedObject.\n";
          print "$line\n";
       }
       if ($line =~ /\bcPolymorphic\b/) {
          print "*** warning at $fname:$lineno: cPolymorhic got renamed to cObject.\n";
          print "$line\n";
       }

       # simtime_t
       if ($line =~ /\bdouble +[a-z0-9_]*(time|age|interval|delay|\bt[0-9]*\b)/i) {
          print "*** warning at $fname:$lineno: This variable might represent simulation time -- if so, change \"double\" to \"simtime_t\".\n";
          print "$line\n";
       }
       if ($line =~ /\b(simtimeToStr|strToSimtime)\b/) {
          print "*** warning at $fname:$lineno: The simtimeToStr() and strToSimtime() methods are no longer supported. (use SimTime methods, or the SIMTIME_STR(t), SIMTIME_DBL(t), STR_SIMTIME(s), SIMTIME_TTOA(buf,t) macros instead).\n";
          print "$line\n";
       }

       # gates
       if ($line =~ /=.*\bsetGateSize\b/) {
          print "*** warning at $fname:$lineno: setGateSize() return type changed to void.\n";
          print "$line\n";
       }
       if ($line =~ /=.*\baddGate\b/) {
          print "*** warning at $fname:$lineno: addGate() return type changed to void, use gate(\"name\") to get the pointer.\n";
          print "$line\n";
       }
       if ($line =~ /'[IO]'/) {
          print "*** warning at $fname:$lineno: if 'I'/'O' refers to a gate type, change it to cGate::INPUT or cGate::OUTPUT.\n";
          print "$line\n";
       }
       if ($line =~ /\b(setTo|setFrom)\b/) {
          print "*** warning at $fname:$lineno: The setTo() and setFrom() methods are no longer supported, use connectTo() instead.\n";
          print "$line\n";
       }

       # messages
       if ($line =~ /\b(setKind|setPriority)\b/ || $line =~ /\bint\b.*(\bkind\(\)|\bpriority\(\))/) {
          print "*** warning at $fname:$lineno: note: message kind and priority changed from int to short, you may want to update the code if you haven't already.\n";
          print "$line\n";
       }
       if ($line =~ /\b(setLength|setByteLength|addLength|addByteLength)\b/ || $line =~ /\blong\b.*(\blength\(\)|\bbyteLength\(\))/) {
          print "*** warning at $fname:$lineno: note: message length changed from long to int64, you may want to update the code if you haven't already.\n";
          print "$line\n";
       }

       # misc
       if ($line =~ /\bcBag\b/) {
          print "*** warning at $fname:$lineno: cBag no longer exists, use std::vector instead.\n";
          print "$line\n";
       }
       if ($line =~ /\bitems\(\)/) {
          print "*** warning at $fname:$lineno: cArray::items() renamed to size().\n";
          print "$line\n";
       }

       # dynamic module creation
       if ($line =~ /\bbuildInside\b/) {
          print "*** warning at $fname:$lineno: finalizeParameters() must be called (for channels, this reads input params from omnetpp.ini; for modules, also creates gates [since gate vector sizes may depend on parameter values])\n";
          print "$line\n";
       }

       # exceptions must be thrown by value
       if ($line =~ /\bthrow +new\b/) {
          print "*** warning at $fname:$lineno: Exceptions MUST be thrown by value, please remove the \"new\" keyword\n";
          print "$line\n";
       }

       # cModule
       if ($line =~ /\bcSubmodIterator\b/) {
          print "*** warning at $fname:$lineno: Deprecated cSubmodIterator. Use cModule::SubmoduleIterator instead\n";
          print "$line\n";
       }
       if ($line =~ /\bmoduleState\b/) {
          print "*** warning at $fname:$lineno: Deprecated moduleState. \n";
          print "$line\n";
       }
       if ($line =~ /\bend\(\)\b/) {
          print "*** warning at $fname:$lineno: cSimpleModule::end() -- removed, as there was little value in it. To terminate an activity() module, simply return from the activity() method, or call the new halt() method to end the module while preserving the local variables for inspection.\n";
          print "$line\n";
       }

       # display string tags
       if ($line =~ /\bbackgroundDisplayString\b/i) {
          print "*** warning at $fname:$lineno: There are no separate backgroundDisplayString and displayString. Use displayString instead. P tag become BGP, B tag become BGB.\n";
          print "$line\n";
       }
    }
    open(OUTFILE, ">$fname") || die "cannot open $fname for write";
    print OUTFILE $txt || die "cannot write $fname";
    close OUTFILE;
}

print "\nConversion done. You may safely re-run this script as many times as you want.\n";




















































