//=========================================================================
//  CPTRPASSINGMPICOMMBUFFER.H - part of
//
//                  OMNeT++/OMNEST
//           Discrete System Simulation in C++
//
//  Author: Andras Varga, 2005
//          Dept. of Electrical and Computer Systems Engineering,
//          Monash University, Melbourne, Australia
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 2003-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __CPTRPASSINGMPICOMMBUFFER_H__
#define __CPTRPASSINGMPICOMMBUFFER_H__

#include "cmpicommbuffer.h"

NAMESPACE_BEGIN


/**
 * A variant of cMPICommBuffer. packObject() only packs the pointer
 * and does not serialize object fields. This class can only be
 * used with 64-bit shared memory superclusters like SGI Altix.
 *
 * @ingroup Parsim
 */
class SIM_API cPtrPassingMPICommBuffer : public cMPICommBuffer
{
  public:
    /**
     * Constructor.
     */
    cPtrPassingMPICommBuffer();

    /**
     * Packs an object.
     */
    virtual void packObject(cObject *obj);

    /**
     * Unpacks and returns an object.
     */
    virtual cObject *unpackObject();
};

NAMESPACE_END


#endif

