//==========================================================================
//  TKUTIL.CC -
//            for the Tcl/Tk windowing environment of
//                            OMNeT++
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2004 Andras Varga

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

// Work around an MSVC .NET bug: WinSCard.h causes compilation
// error due to faulty LPCVOID declaration, so prevent it from
// being pulled in (we don't need the SmartCard API here anyway ;-)
#define _WINSCARD_H_

#include <stdio.h>
#include <string.h>
#include <time.h>
#include <assert.h>
#include <tk.h>

#include "cobject.h"
#include "cmodule.h"
#include "csimul.h"
#include "tkapp.h"
#include "patmatch.h"
#include "visitor.h"
#include "tkutil.h"



TclQuotedString::TclQuotedString(const char *s)
{
    int flags;
    int quotedlen = Tcl_ScanElement(TCLCONST(s), &flags);
    quotedstr = quotedlen<80 ? buf : Tcl_Alloc(quotedlen+1);
    Tcl_ConvertElement(TCLCONST(s), quotedstr, flags);
}

TclQuotedString::TclQuotedString(const char *s, int n)
{
    int flags;
    int quotedlen = Tcl_ScanCountedElement(TCLCONST(s), n, &flags);
    quotedstr = quotedlen<80 ? buf : Tcl_Alloc(quotedlen+1);
    Tcl_ConvertCountedElement(TCLCONST(s), n, quotedstr, flags);
}

TclQuotedString::~TclQuotedString()
{
    if (quotedstr!=buf)
        Tcl_Free(quotedstr);
}

//=======================================================================

char *ptrToStr(void *ptr, char *buffer)
{
    static char staticbuf[20];
    if (buffer==NULL)
           buffer = staticbuf;

    if (ptr==0)
           strcpy(buffer,"ptr0");  // GNU C++'s sprintf() says "nil"
    else
           sprintf(buffer,"ptr%p", ptr );
    return buffer;
}

void *strToPtr(const char *s)
{
    if (s[0]!='p' || s[1]!='t' || s[2]!='r')
       return NULL;

    void *ptr;
    sscanf(s+3,"%p",&ptr);
    return ptr;
}


void setObjectListResult(Tcl_Interp *interp, cCollectObjectsVisitor *visitor)
{
   int n = visitor->getArraySize();
   cObject **objs = visitor->getArray();
   const int ptrsize = 21; // one ptr should be max 20 chars (good for even 64bit-ptrs)
   char *buf = Tcl_Alloc(ptrsize*n);
   char *s=buf;
   for (int i=0; i<n; i++)
   {
       ptrToStr(objs[i],s);
       assert(strlen(s)<=20);
       s+=strlen(s);
       *s++ = ' ';
   }
   *s='\0';
   Tcl_SetResult(interp, buf, TCL_DYNAMIC);
}


//-----------------------------------------------------------------------

void feedCollectionIntoInspectorListbox(cCollectObjectsVisitor *visitor, Tcl_Interp *interp, const char *listbox, bool fullpath)
{
    int n = visitor->getArraySize();
    cObject **objs = visitor->getArray();
    for (int i=0; i<n; i++)
    {
        cObject *obj = objs[i];
        const char *ptr = ptrToStr(obj);
        static char buf[MAX_OBJECTINFO];
        obj->info(buf);
        CHK(Tcl_VarEval(interp, "multicolumnlistbox_insert ",listbox," ",ptr," {"
                                "ptr {",ptr,"} "
                                "name {",(fullpath ? obj->fullPath() : obj->fullName()),"} "
                                "class {",obj->className(),"} "
                                "info {",buf,"}"
                                "}",NULL));
    }
}

int fillListboxWithChildObjects(cObject *object, Tcl_Interp *interp, const char *listbox, bool deep)
{
    int n;
    if (deep)
    {
        cCollectObjectsVisitor visitor;
        visitor.setSizeLimit(100000); // FIXME hardwired constant!
        visitor.process(object);
        n = visitor.getArraySize();
        feedCollectionIntoInspectorListbox(&visitor, interp, listbox, true);
    }
    else
    {
        cCollectChildrenVisitor visitor(object);
        visitor.setSizeLimit(100000); // FIXME hardwired constant!
        visitor.process(object);
        n = visitor.getArraySize();
        feedCollectionIntoInspectorListbox(&visitor, interp, listbox, false);
    }
    return n;
}


//FIXME obsolete?
static void insert_into_inspectorlistbox(Tcl_Interp *interp, const char *listbox, cObject *obj, bool fullpath)
{
    const char *ptr = ptrToStr(obj);
    static char buf[MAX_OBJECTINFO];
    obj->info(buf);
    CHK(Tcl_VarEval(interp, "multicolumnlistbox_insert ",listbox," ",ptr," {"
                            "ptr {",ptr,"} "
                            "name {",(fullpath ? obj->fullPath() : obj->fullName()),"} "
                            "class {",obj->className(),"} "
                            "info {",buf,"}"
                            "}",NULL));
}

static void do_fill_module_listbox(cModule *parent, Tcl_Interp *interp, const char *listbox, bool simpleonly, bool deep)
{
    // loop through module vector
    for (int i=0; i<=simulation.lastModuleId() && !memoryIsLow(); i++ )
    {
        cModule *mod = simulation.module(i);
        if (mod && mod!=simulation.systemModule() && mod->parentModule()==parent)
        {
           if (!simpleonly || mod->isSimple())
              insert_into_inspectorlistbox(interp, listbox, mod, deep);

           // handle 'deep' option using recursivity
           if (deep)
              do_fill_module_listbox(mod,interp,listbox,simpleonly,deep);
        }
    }
}

int fillListboxWithChildModules(cModule *parent, Tcl_Interp *interp, const char *listbox, bool simpleonly, bool deep)
{
    if (deep)
    {
        if (!simpleonly || parent->isSimple())
            insert_into_inspectorlistbox(interp, listbox, parent,deep);
    }
    do_fill_module_listbox(parent,interp,listbox,simpleonly,deep);
    return 0; //FIXME!!!!!!!!!
}

//----------------------------------------------------------------------

static bool do_inspect_by_name( cObject *obj, bool beg, const char *_fullpath, const char *_classname,
                                int _insptype, const char *_geometry)
{
    static const char *fullpath;
    static const char *classname;
    static int insptype;
    static const char *geometry;

    static int ctr;
    if (!obj) {       // setup
        fullpath = _fullpath;
        classname = _classname;
        insptype = _insptype;
        geometry = _geometry;
        ctr  = 0;
        return false;
    }

    if (!beg)
        return false;

    // we have to do exhaustive search here... optimization, such as checking
    // if objpath matches beginning of fullpath to see if we're on the
    // right track is not usable, because some objects (simulation, modules'
    // paramv, gatev members) don't appear in object's fullPath()...

    const char *objpath = obj->fullPath();

    // however, a module's name and the future event set's name is not hidden,
    // so if this obj is a module (or cMessageHeap) and its name doesn't match
    // the beginning of fullpath, we can cut the search here.
    if ((dynamic_cast<cModule *>(obj) || dynamic_cast<cMessageHeap *>(obj))
        && strncmp(objpath, fullpath, strlen(objpath))!=0)
    {
        // skip (do not search) this subtree
        return false;
    }

    // found it?
    if (!strcmp(fullpath,objpath) && !strcmp(classname,obj->className()))
    {
        // found: inspect if inspector is not open
        TOmnetTkApp *app = getTkApplication();
        if (!app->findInspector(obj, insptype))
            app->inspect(obj, insptype, geometry, NULL);
        // makes no sense to go further down
        return false;
    }

    // just search further...
    return true;
}


void inspectObjectByName(const char *fullpath, const char *classname, int insptype, const char *geometry)
{
    // open inspectors for object whose is the same as fullpath
    do_inspect_by_name(NULL,false, fullpath, classname, insptype, geometry);
    simulation.forEach( (ForeachFunc)do_inspect_by_name);
}


