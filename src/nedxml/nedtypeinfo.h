//==========================================================================
// NEDTYPEINFO.H -
//
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 2002-2015 Andras Varga
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/


#ifndef __OMNETPP_NEDXML_NEDTYPEINFO_H
#define __OMNETPP_NEDXML_NEDTYPEINFO_H

#include <map>
#include <vector>
#include <string>
#include "common/commonutil.h"
#include "nedelements.h"

namespace omnetpp {
namespace nedxml {

class NEDResourceCache;

/**
 * Wraps a NEDElement tree of a NED declaration (module, channel, module
 * interface or channel interface), or declaration in a msg file (enum,
 * class, struct). May be extended by subclassing.
 *
 * Represents NED declarations of modules, module interfaces,
 * channels and channel interfaces. All instances are created and managed
 * by NEDResourceCache.
 *
 * @ingroup NEDResources
 */
class NEDXML_API NEDTypeInfo
{
  public:
    enum Type {SIMPLE_MODULE, COMPOUND_MODULE, MODULEINTERFACE, CHANNEL, CHANNELINTERFACE};

  protected:
    // the resolver this type is in
    NEDResourceCache *resolver;

    Type type;
    std::string qualifiedName;
    bool isInner;  // whether it is an inner type
    NEDElement *tree; // points into resolver

    typedef std::vector<std::string> StringVector;
    typedef std::map<std::string,int> StringToIntMap;

    // inheritance. Vectors contain fully qualifies names, and include
    // indirect base types/interfaces as well (transitive closure).
    StringVector extendsNames;
    StringVector interfaceNames;

    std::string enclosingTypeName;

    // simple module/channel C++ class to instantiate
    std::string implClassName;

  protected:
    void checkComplianceToInterface(NEDTypeInfo *interfaceDecl);

  public:
    /** Constructor. It expects fully qualified name */
    NEDTypeInfo(NEDResourceCache *resolver, const char *qname, bool isInnerType, NEDElement *tree);

    /** Destructor */
    virtual ~NEDTypeInfo();

    /** Returns the simple name of the NED type */
    virtual const char *getName() const;

    /** Returns the fully qualified name of the NED type */
    virtual const char *getFullName() const;

    /** Returns the raw NEDElement tree representing the component */
    virtual NEDElement *getTree() const;

    /** The NED type resolver this type is registered in */
    NEDResourceCache *getResolver() const  {return resolver;}

    /**
     * Returns the type of this declaration: simple module, compound module,
     * channel, etc.
     */
    virtual Type getType() const {return type;}

    /**
     * Returns the name of the file this NED type was loaded from.
     */
    virtual const char *getSourceFileName() const;

    /**
     * Returns the package name (from the package declaration of the containing
     * NED file.)
     */
    virtual std::string getPackage() const;

    /**
     * Returns a one-line summary (base class, implemented interfaces, etc)
     */
    virtual std::string info() const;

    /**
     * Returns the NED declaration.
     */
    virtual std::string nedSource() const;

    /**
     * Returns the number of "extends" names. This includes indirect
     * base types as well (i.e. base types of base types, etc).
     */
    virtual int numExtendsNames() const  {return extendsNames.size();}

    /**
     * Returns the name of the kth "extends" name (k=0..numExtendsNames()-1),
     * resolved to fully qualified name.
     */
    virtual const char *extendsName(int k) const;

    /**
     * Returns the number of interfaces. This includes indirectly implemented
     * interfaces as well. (That is, the list contains interfaces implemented
     * by this type and all its base types, plus base types of all those
     * interfaces).
     */
    virtual int numInterfaceNames() const  {return interfaceNames.size();}

    /**
     * Returns the name of the kth interface (k=0..numInterfaceNames()-1),
     * resolved to fully qualified name.
     */
    virtual const char *interfaceName(int k) const;

    /**
     * Returns true if this NED type extends/"is like" the given module interface
     * or channel interface
     */
    virtual bool supportsInterface(const char *qname);

    /**
     * Returns true if this NED type is an inner type
     */
    virtual bool isInnerType() const  {return isInner;}

    /**
     * If this type is an inner type, returns fully qualified name of its
     * enclosing type, otherwise returns nullptr.
     */
    virtual const char *getEnclosingTypeName() const;

    /**
     * Returns true if this NED type has a local (non-inherited)
     * \@network (or \@network(true)) property.
     */
    virtual bool isNetwork() const;

    /**
     * For modules and channels, it returns the name of the C++ class that
     * has to be instantiated (for compound modules this defaults to
     * "cModule"); for interface types it returns nullptr.
     */
    virtual const char *getImplementationClassName() const;

    /**
     * Find a property with the given name in the type's NED file, then in the
     * package.ned file of the NED file, then in parent package.ned files up
     * to the root (the NED source folder this NED file is in). Returns
     * the simple value of the property (1st value of default key), or
     * empty string if not found.
     */
    virtual std::string getPackageProperty(const char *name) const;

    /** The C++ namespace for this NED type; implemented as getPackageProperty("namespace"). */
    virtual std::string getCxxNamespace() const;

    /** Returns the first "extends" clause, or nullptr */
    virtual NEDTypeInfo *getSuperDecl() const;

    /** @name Convenience method to query the tree */
    //@{
    ParametersElement *getParametersElement() const;
    GatesElement *getGatesElement() const;
    SubmodulesElement *getSubmodulesElement() const;
    ConnectionsElement *getConnectionsElement() const;

    /** Returns the submodule element with the given name from the local type, or nullptr if not found */
    SubmoduleElement *getLocalSubmoduleElement(const char *submoduleName) const;

    /** Returns the connection element with the given id from the local type, or nullptr if not found */
    ConnectionElement *getLocalConnectionElement(long id) const;

    /** Returns the submodule element with the given name from the local type and "extends" types, or nullptr if not found */
    SubmoduleElement *getSubmoduleElement(const char *submoduleName) const;

    /** Returns the connection element with the given id from the local type and "extends" types, or nullptr if not found */
    ConnectionElement *getConnectionElement(long id) const;

    /** Searches local type; nullptr if not found */
    ParamElement *findLocalParamDecl(const char *name) const;

    /** Searches local type and "extends" types; nullptr if not found */
    ParamElement *findParamDecl(const char *name) const;

    /** Searches local type; nullptr if not found */
    GateElement *findLocalGateDecl(const char *name) const;

    /** Searches local type and "extends" types; nullptr if not found */
    GateElement *findGateDecl(const char *name) const;
    //@}
};

} // namespace nedxml
}  // namespace omnetpp


#endif

