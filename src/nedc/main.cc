//==========================================================================
//   MAIN.CC -
//            part of OMNeT++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2001 Andras Varga
  Technical University of Budapest, Dept. of Telecommunications,
  Stoczek u.2, H-1111 Budapest, Hungary.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/


#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fstream.h>

#include "nedparser.h"
#include "nedxmlparser.h"
#include "neddtdvalidator.h"
#include "nedbasicvalidator.h"
#include "nedsemanticvalidator.h"
#include "nedgenerator.h"
#include "xmlgenerator.h"
#include "cppgenerator.h"
#include "nedcompiler.h"


#if defined(_WIN32) && !defined(__CYGWIN32__)

//
// On Windows, wildcards must be expanded by hand :-(
//
#define MUST_EXPAND_WILDCARDS 1
const char *findFirstFile(const char *mask);
const char *findNextFile();

#if defined(_MSC_VER)

//
// code for MSVC
//
#include <io.h>
long handle;
struct _finddata_t fdata;
const char *findFirstFile(const char *mask)
{
    handle = _findfirst(mask, &fdata);
    if (handle<0) {_findclose(handle); return NULL;}
    return fdata.name;
}
const char *findNextFile()
{
    int done=_findnext(handle, &fdata);
    if (done) {_findclose(handle); return NULL;}
    return fdata.name;
}

#else

//
// code for Borland C++
//
#include <dir.h>
struct ffblk ffblk;
const char *findFirstFile(const char *mask)
{
    int done = findfirst(argv[k],&ffblk,0);
    return done ? NULL : ffblk.ff_name;
}
const char *findNextFile()
{
    int done = findnext(&ffblk);
    return done ? NULL : ffblk.ff_name;
}

#endif

#endif /* _WIN32 */


// file types
enum {XML, NED, CPP, NOTHING};

// option variables
bool opt_gencpp = false;           // -c
bool opt_genxml = false;           // -x
bool opt_genned = false;           // -n
bool opt_validateonly = false;     // -v
int opt_nextfiletype = NOTHING;    // -X
bool opt_newsyntax = false;        // -N
const char *opt_suffix = NULL;     // -s
bool opt_unparsedexpr = false;     // -e
bool opt_novalidation = false;     // -y
bool opt_noimports = false;        // -z
bool opt_srcloc = false;           // -p
bool opt_mergeoutput = false;      // -m
bool opt_verbose = false;          // -V
const char *opt_outputfile = NULL; // -o


// global variables
NEDFileCache filecache;
NEDClassicImportResolver importresolver;

NedFilesNode *outputtree;


void printUsage()
{
    fprintf(stderr,
       "nedtool -- part of OMNeT++, (c) 2002 Andras Varga\n"
       "Syntax: nedtool [options] <files>\n"
       "  -c: generate C++ (default)\n"
       "  -x: generate XML\n"
       "  -n: generate NED\n"
       "  -v: no output (only validate input)\n"
       "  -m: output is a single file (out_n.* by default)\n"
       "  -o <filename>: with -m: output file name\n"
       "  -I <dir>: add directory to NED include path\n"
       "  -X xml/ned/off: following files are XML/NED up to '-X off'\n"
       "  -N: with -n: use new NED syntax (e.g. module Foo {...})\n"
       "  -s <suffix>: suffix for generated files\n"
       "  -e: do not parse expressions in NED input; expect unparsed expressions in XML\n"
       "  -y: do not load imports and also skip semantic validation\n"
       "  -z: do not load imports\n"
       "  -p: with -x: add source location info (src-loc attributes) to XML output\n"
       "  -V: verbose\n"
       "Note: this program represents work in progress, parts of it are incomplete, and\n"
       "some functionality is completely missing.\n"
       "\n"
    );
}

// FIXME todo: negate -e, -y for XML and NED output; --; - as filename

bool processFile(const char *fname)
{
    if (opt_verbose) fprintf(stderr,"processing '%s'...\n",fname);

    // determine file type
    int ftype = opt_nextfiletype;
    if (ftype==NOTHING)
    {
        if (!strcmp(".ned", fname+strlen(fname)-4))
            ftype=NED;
        if (!strcmp(".xml", fname+strlen(fname)-4))
            ftype=XML;
        if (ftype==NOTHING)
            ftype=NED;
    }

    // process input tree
    NEDElement *tree = 0;
    if (ftype==XML)
    {
        tree = parseXML(fname);
    }
    else if (ftype==NED)
    {
        NEDParser parser;
        parser.parseFile(fname,!opt_unparsedexpr);
        tree = parser.getTree();
    }

    if (!tree)
        return false;

    // DTD validation and additional basic validation
    NEDDTDValidator dtdvalidator;
    dtdvalidator.validate(tree);
    NEDBasicValidator basicvalidator(!opt_unparsedexpr);
    basicvalidator.validate(tree);

    // semantic validation (will load imports too)
    if (!opt_novalidation)
    {
        if (!opt_noimports)
        {
            // invoke NEDCompiler (will process imports and do semantic validation)
            NEDSymbolTable symboltable;
            NEDCompiler nedc(&filecache, &symboltable, &importresolver);
            nedc.validate(tree);
        }
        else
        {
            // simple semantic validation (without imports)
            NEDSymbolTable symboltable;
            NEDSemanticValidator validator(!opt_unparsedexpr,&symboltable);
            validator.validate(tree);
        }
    }

    if (opt_mergeoutput)
    {
        outputtree->appendChild(tree);
    }
    else if (!opt_validateonly)
    {
        // generate output file name
        char outfname[1024];
        strcpy(outfname,fname);
        char *s = outfname+strlen(outfname);
        while (s>outfname && *s!='/' && *s!='\\' && *s!='.') s--;
        if (*s!='.') s=outfname+strlen(outfname);
        const char *suffix = opt_suffix;
        if (!suffix)
        {
            if (opt_genxml)
                suffix = "_n.xml";
            else if (opt_genned)
                suffix = "_n.ned";
            else
                suffix = "_n.cc";
        }
        strcpy(s,suffix);

        // output file. FIXME checks here, after..
        ofstream out(outfname);

        if (opt_genxml)
            generateXML(out, tree, opt_srcloc);
        else if (opt_genned)
            generateNed(out, tree, opt_newsyntax);
        else
            generateCpp(out, tree);
        out.close();

        delete tree;
    }
    return true;
}

int main(int argc, char **argv)
{
    // print usage
    if (argc<2)
    {
        printUsage();
        return 1;
    }

    // process options
    for (int i=1; i<argc; i++)
    {
        if (!strcmp(argv[i],"-c"))
        {
            opt_gencpp = true;
        }
        else if (!strcmp(argv[i],"-x"))
        {
            opt_genxml = true;
        }
        else if (!strcmp(argv[i],"-n"))
        {
            opt_genned = true;
        }
        else if (!strcmp(argv[i],"-v"))
        {
            opt_validateonly = true;
        }
        else if (!strcmp(argv[i],"-I"))
        {
            i++;
            if (i==argc) {
                fprintf(stderr,"unexpected end of arguments after -I\n");
                return 1;
            }
            importresolver.addImportPath(argv[i]);
        }
        else if (!strcmp(argv[i],"-X"))
        {
            i++;
            if (i==argc) {
                fprintf(stderr,"unexpected end of arguments after -X\n");
                return 1;
            }
            if (!strcmp(argv[i],"ned"))
                opt_nextfiletype = NED;
            else if (!strcmp(argv[i],"xml"))
                opt_nextfiletype = XML;
            else if (!strcmp(argv[i],"off"))
                opt_nextfiletype = NOTHING;
            else {
                fprintf(stderr,"unknown file type %s after -X\n",argv[i]);
                return 1;
            }
        }
        else if (!strcmp(argv[i],"-N"))
        {
            opt_newsyntax = true;
        }
        else if (!strcmp(argv[i],"-s"))
        {
            i++;
            if (i==argc) {
                fprintf(stderr,"unexpected end of arguments after -s\n");
                return 1;
            }
            opt_suffix = argv[i];
        }
        else if (!strcmp(argv[i],"-e"))
        {
            opt_unparsedexpr = true;
        }
        else if (!strcmp(argv[i],"-y"))
        {
            opt_novalidation = true;
        }
        else if (!strcmp(argv[i],"-z"))
        {
            opt_noimports = true;
        }
        else if (!strcmp(argv[i],"-p"))
        {
            opt_srcloc = true;
        }
        else if (!strcmp(argv[i],"-m"))
        {
            opt_mergeoutput = true;
            outputtree = new NedFilesNode;
        }
        else if (!strcmp(argv[i],"-o"))
        {
            i++;
            if (i==argc) {
                fprintf(stderr,"unexpected end of arguments after -o\n");
                return 1;
            }
            opt_outputfile = argv[i];
        }
        else if (!strcmp(argv[i],"-V"))
        {
            opt_verbose = true;
        }
        else if (argv[i][0]=='-')
        {
            fprintf(stderr,"unknown flag %s\n",argv[i]);
            return 1;
        }
        else
        {
#ifdef MUST_EXPAND_WILDCARDS
            const char *fname = findFirstFile(argv[i]);
            if (!fname) {
                fprintf(stderr,"%s: not found: %s\n",argv[0],argv[i]);
                return 1;
            }
            while (fname)
            {
                if (!processFile(fname)) return 1;
                fname = findNextFile();
            }
#else
            if (!processFile(argv[i])) return 1;
#endif
        }
    }

    if (opt_mergeoutput)
    {
        const char *outfname;

        if (opt_outputfile)
            outfname = opt_outputfile;
        else if (opt_genxml)
            outfname = "out_n.xml";
        else if (opt_genned)
            outfname = "out_n.ned";
        else
            outfname = "out_n.cc";

        ofstream out(outfname);

        if (opt_genxml)
            generateXML(out, outputtree, opt_srcloc);
        else if (opt_genned)
            generateNed(out, outputtree, opt_newsyntax);
        else
            generateCpp(out, outputtree);
        out.close();

        delete outputtree;
    }

    return 0;
}


