//=========================================================================
//  NEDFUNCTIONS.CC - part of
//
//                    OMNeT++/OMNEST
//             Discrete System Simulation in C++
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2008 Andras Varga
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include <math.h>
#include <string.h>
#include "distrib.h"
#include "cmathfunction.h"
#include "cnedfunction.h"
#include "cexception.h"
#include "cstringtokenizer.h"
#include "unitconversion.h"
#include "stringutil.h"
#include "opp_ctype.h"

USING_NAMESPACE

//FIXME cDynamicExpression to add function name to exceptions thrown from functions

void nedfunctions_dummy() {} //see util.cc

typedef cDynamicExpression::Value Value;  // abbreviation for local use

#define DEF(FUNCTION, SIGNATURE, CATEGORY, DESCRIPTION, BODY) \
    static Value FUNCTION(cComponent *context, Value argv[], int argc) {BODY} \
    Define_NED_Function2(FUNCTION, SIGNATURE, CATEGORY, DESCRIPTION);


//
// NED math.h functions
//

Define_Function(acos, 1)
Define_Function(asin, 1)
Define_Function(atan, 1)
Define_Function(atan2, 2)

Define_Function(sin, 1)
Define_Function(cos, 1)
Define_Function(tan, 1)

Define_Function(ceil, 1)
Define_Function(floor, 1)

Define_Function(exp, 1)
Define_Function(pow, 2)
Define_Function(sqrt, 1)

Define_Function(hypot, 2)

Define_Function(log, 1)
Define_Function(log10, 1)


DEF(nedf_fabs, "quantity fabs(quantity x)", "math", "", {
    argv[0].dbl = fabs(argv[0].dbl);  // preserve unit
    return argv[0];
})

DEF(nedf_fmod, "quantity fmod(quantity x, quantity y)", "math", "", {
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = fmod(argv[0].dbl, argv1converted);
    return argv[0];
})

DEF(nedf_min, "quantity min(quantity a, quantity b)", "math", "", {
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    return argv[0].dbl < argv1converted ? argv[0] : argv[1];
})

DEF(nedf_max, "quantity max(quantity a, quantity b)", "math", "", {
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    return argv[0].dbl < argv1converted ? argv[1] : argv[0];
})


//
// Unit handling
//

static cStringPool stringPool;

DEF(nedf_dropUnit, "double dropUnit(quantity x)", "unit", "", {
    argv[0].dblunit = NULL;
    return argv[0];
})

DEF(nedf_replaceUnit, "quantity replaceUnit(quantity x, string unit)", "unit", "", {
    argv[0].dblunit = stringPool.get(argv[1].s.c_str());
    return argv[0];
})

DEF(nedf_convertUnit, "quantity convertUnit(quantity x, string unit)", "unit", "", {
    const char *newUnit = stringPool.get(argv[1].s.c_str());
    argv[0].dbl = UnitConversion::convertUnit(argv[0].dbl, argv[0].dblunit, newUnit);
    argv[0].dblunit = newUnit;
    return argv[0];
})

DEF(nedf_unitOf, "string unitOf(quantity x)", "unit", "Returns the unit of the given quantity.", {
    return argv[0].dblunit;
})


//
// String manipulation functions.
//

DEF(nedf_length, "long length(string s)", "strings", "", {
    return (long)argv[0].s.size();
})

DEF(nedf_contains, "bool contains(string s, string substr)", "strings", "", {
    return argv[0].s.find(argv[1].s) != std::string::npos;
})

DEF(nedf_substring, "string substring(string s, long index, long len?)", "strings", "", {  // Note: substring(str, index[, length]), i.e. length is optional
    int size = argv[0].s.size();
    int index = (int)argv[1].dbl;
    int length = argc==3 ? (int)argv[2].dbl : size-index;

    if (index < 0 || index > size)
        throw cRuntimeError("substring(): index out of bounds");
    if (length < 0)
        throw cRuntimeError("substring(): length is negative");
    return argv[0].s.substr(index, length);
})

DEF(nedf_substringBefore, "string substringBefore(string s, string substr)", "strings", "", {
    size_t pos = argv[0].s.find(argv[1].s);
    return pos==std::string::npos ? "" : argv[0].s.substr(0,pos);
})

DEF(nedf_substringAfter, "string substringAfter(string s, string substr)", "strings", "", {
    size_t pos = argv[0].s.find(argv[1].s);
    return pos==std::string::npos ? "" : argv[0].s.substr(pos+argv[1].s.size());
})

DEF(nedf_substringBeforeLast, "string substringBeforeLast(string s, string substr)", "strings", "", {
    size_t pos = argv[0].s.rfind(argv[1].s);
    return pos==std::string::npos ? "" : argv[0].s.substr(0,pos);
})

DEF(nedf_substringAfterLast, "string substringAfterLast(string s, string substr)", "strings", "", {
    size_t pos = argv[0].s.rfind(argv[1].s);
    return pos==std::string::npos ? "" : argv[0].s.substr(pos+argv[1].s.size());
})

DEF(nedf_startsWith, "bool startsWith(string s, string substr)", "strings", "", {
    return argv[0].s.find(argv[1].s) == 0;
})

DEF(nedf_endsWith, "bool endsWith(string s, string substr)", "strings", "", {
    return argv[0].s.rfind(argv[1].s) == argv[0].s.size() - argv[1].s.size();
})

DEF(nedf_tail, "string tail(string s, long len)", "", "strings", {
    int length = (int)argv[1].dbl;
    if (length < 0)
        throw cRuntimeError("tail(): length is negative");
    int size = (int) argv[0].s.size();
    return argv[0].s.substr(std::max(0, size - length), size);
})

DEF(nedf_replace, "string replace(string s, string substr, string repl, long startPos?)", "strings", "", {
    std::string str = argv[0].s;
    std::string& search = argv[1].s;
    std::string& replacement = argv[2].s;
    size_t index = 0;
    if (argc==4) {
        if (argv[3].dbl < 0)
            throw cRuntimeError("replace(): start index is negative");
        index = (size_t)argv[3].dbl;
        if (index > str.size())
            throw cRuntimeError("replace(): start index out of bounds");
    }

    size_t searchSize = search.size();
    size_t replacementSize = replacement.size();
    while ((index = str.find(search, index)) != std::string::npos) {
        str.replace(index, searchSize, replacement);
        index += replacementSize - searchSize + 1;
    }
    return str;
})

DEF(nedf_replaceFirst, "string replaceFirst(string s, string substr, string repl, long startPos?)", "strings", "", {
    std::string str = argv[0].s;
    std::string& search = argv[1].s;
    std::string& replacement = argv[2].s;
    size_t index = 0;
    if (argc==4) {
        if (argv[3].dbl < 0)
            throw cRuntimeError("replaceFirst(): start index is negative");
        index = (size_t)argv[3].dbl;
        if (index > str.size())
            throw cRuntimeError("replaceFirst(): start index out of bounds");
    }

    size_t searchSize = search.size();
    if ((index = str.find(search, index)) != std::string::npos)
        str.replace(index, searchSize, replacement);
    return str;
})

DEF(nedf_trim, "string trim(string s)", "", "strings", {
    return opp_trim(argv[0].s.c_str());
})

DEF(nedf_indexOf, "long indexOf(string s, string substr)", "strings", "", {
    return (long)argv[0].s.find(argv[1].s);
})

DEF(nedf_choose, "string choose(long index, string list)", "strings", "", {
    int index = (int)argv[0].dbl;
    if (index < 0)
        throw cRuntimeError("choose(): negative index");
    cStringTokenizer tokenizer(argv[1].s.c_str());
    for (int i=0; i<index && tokenizer.hasMoreTokens(); i++)
        tokenizer.nextToken();
    if (!tokenizer.hasMoreTokens())
        throw cRuntimeError("choose(): index out of bounds: %d", index);
    return tokenizer.nextToken();
})

DEF(nedf_toUpper, "string toUpper(string s)", "strings", "", {
    std::string tmp = argv[0].s;
    int length = tmp.length();
    for (int i=0; i<length; i++)
        tmp[i] = opp_toupper(tmp[i]);
    return tmp;
})

DEF(nedf_toLower, "string toLower(string s)", "strings", "", {
    std::string tmp = argv[0].s;
    int length = tmp.length();
    for (int i=0; i<length; i++)
        tmp[i] = opp_tolower(tmp[i]);
    return tmp;
})

DEF(nedf_int, "long int(any x)", "conversion", "", {
    switch (argv[0].type) {
        case Value::BOOL:
            return argv[0].bl ? 1L : 0L;
        case Value::DBL:
            return (long)floor(argv[0].dbl);
        case Value::STR:
            return (long)floor(opp_atof(argv[0].s.c_str()));  //XXX catch & wrap exception?
        case Value::XML:
            throw cRuntimeError("int(): cannot convert xml to int");
        default:
            throw cRuntimeError("internal error: bad Value type");
    }
})

DEF(nedf_double, "double double(any x)", "conversion", "", {
    switch (argv[0].type) {
        case Value::BOOL:
            return argv[0].bl ? 1.0 : 0.0;
        case Value::DBL:
            return argv[0].dbl;
        case Value::STR:
            return opp_atof(argv[0].s.c_str());  //XXX catch & wrap exception?
        case Value::XML:
            throw cRuntimeError("double(): cannot convert xml to double");
        default:
            throw cRuntimeError("internal error: bad Value type");
    }
})

DEF(nedf_string, "string string(any x)", "conversion", "", {
    return argv[0].str();
})


//
// Reflection
//

DEF(nedf_fullPath, "string fullPath()", "ned", "", {
    return context->getFullPath();
})

DEF(nedf_fullName, "string fullName()", "ned", "", {
    return context->getFullName();
})

DEF(nedf_parentIndex, "long parentIndex()", "ned", "", {
    cModule *mod = context->getParentModule();
    if (!mod)
        throw cRuntimeError("parentIndex(): `%s' has no parent module", context->getFullPath().c_str());
    if (!mod->isVector())
        throw cRuntimeError("parentIndex(): module `%s' is not a vector", mod->getFullPath().c_str());
    return (long)mod->getIndex();
})

DEF(nedf_ancestorIndex, "long ancestorIndex(long levels)", "ned", "", {
    int levels = (int)argv[0].dbl;
    if (levels<0)
        throw cRuntimeError("ancestorIndex(): negative number of levels");
    if (levels==0 && !context->isModule())
        throw cRuntimeError("ancestorIndex(): numlevels==0 and this is not a module");
    cModule *mod = dynamic_cast<cModule *>(context);
    for (int i=0; mod && i<levels; i++)
        mod = mod->getParentModule();
    if (!mod)
        throw cRuntimeError("ancestorIndex(): argument is larger than current nesting level");
    if (!mod->isVector())
        throw cRuntimeError("ancestorIndex(): module `%s' is not a vector", mod->getFullPath().c_str());
    return (long)mod->getIndex();
})


//
// Random variate generation.
//

// continuous
DEF(nedf_uniform, "quantity uniform(quantity a, quantity b, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = uniform(argv[0].dbl, argv1converted, rng);
    return argv[0];
})

DEF(nedf_exponential, "quantity exponential(quantity mean, long rng?)", "random", "", {
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = exponential(argv[0].dbl, rng);
    return argv[0];
})

DEF(nedf_normal, "quantity normal(quantity mean, quantity stddev, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = normal(argv[0].dbl, argv1converted, rng);
    return argv[0];
})

DEF(nedf_truncnormal, "quantity truncnormal(quantity mean, quantity stddev, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = truncnormal(argv[0].dbl, argv1converted, rng);
    return argv[0];
})

DEF(nedf_gamma_d, "quantity gamma_d(double alpha, quantity theta, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[1].dbl = gamma_d(argv[0].dbl, argv[1].dbl, rng);
    return argv[1];
})

DEF(nedf_beta, "double beta(double alpha1, double alpha2, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[0].dbl = beta(argv[0].dbl, argv[1].dbl, rng);
    return argv[0];
})

DEF(nedf_erlang_k, "quantity erlang_k(long k, quantity mean, long rng?)", "random", "", {
    if (argv[0].dbl < 0.0)
       throw cRuntimeError("erlang_k(): k parameter (number of phases) must be positive "
                           "(k=%g", argv[0].dbl);
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[1].dbl = erlang_k((unsigned int)argv[0].dbl, argv[1].dbl, rng);
    return argv[1];
})

DEF(nedf_chi_square, "double chi_square(long k, long rng?)", "random", "", {
    if (argv[0].dbl < 0.0)
       throw cRuntimeError("chi_square(): k parameter (degrees of freedom) must be positive "
                           "(k=%g", argv[0].dbl);
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = chi_square((unsigned int)argv[0].dbl, rng);
    return argv[0];
})

DEF(nedf_student_t, "double student_t(long i, long rng?)", "random", "", {
    if (argv[0].dbl < 0.0)
       throw cRuntimeError("student_t(): i parameter (degrees of freedom) must be positive "
                           "(i=%g", argv[0].dbl);
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = student_t((unsigned int)argv[0].dbl, rng);
    return argv[0];
})

DEF(nedf_cauchy, "quantity cauchy(quantity a, quantity b, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = cauchy(argv[0].dbl, argv1converted, rng);
    return argv[0];
})

DEF(nedf_triang, "quantity triang(quantity a, quantity b, quantity c, long rng?)", "random", "", {
    int rng = argc==4 ? (int)argv[3].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    double argv2converted = UnitConversion::convertUnit(argv[2].dbl, argv[2].dblunit, argv[0].dblunit);
    argv[0].dbl = triang(argv[0].dbl, argv1converted, argv2converted, rng);
    return argv[0];
})

DEF(nedf_lognormal, "double lognormal(double m, double w, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[0].dbl = lognormal(argv[0].dbl, argv[1].dbl, rng);
    return argv[0];
})

DEF(nedf_weibull, "quantity weibull(quantity a, quantity b, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    double argv1converted = UnitConversion::convertUnit(argv[1].dbl, argv[1].dblunit, argv[0].dblunit);
    argv[0].dbl = weibull(argv[0].dbl, argv1converted, rng);
    return argv[0];
})

DEF(nedf_pareto_shifted, "quantity pareto_shifted(double a, quantity b, quantity c, long rng?)", "", "", {
    int rng = argc==4 ? (int)argv[3].dbl : 0;
    double argv2converted = UnitConversion::convertUnit(argv[2].dbl, argv[2].dblunit, argv[1].dblunit);
    argv[1].dbl = pareto_shifted(argv[0].dbl, argv[1].dbl, argv2converted, rng);
    return argv[1];
})

// discrete

DEF(nedf_intuniform, "long intuniform(long a, long b, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[0].dbl = intuniform((int)argv[0].dbl, (int)argv[1].dbl, rng);
    return argv[0];
})

DEF(nedf_bernoulli, "long bernoulli(double p, long rng?)", "random", "", {
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = bernoulli(argv[0].dbl, rng);
    return argv[0];
})

DEF(nedf_binomial, "long binomial(long n, double p, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[0].dbl = binomial((int)argv[0].dbl, argv[1].dbl, rng);
    return argv[0];
})

DEF(nedf_geometric, "long geometric(double p, long rng?)", "random", "", {
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = geometric(argv[0].dbl, rng);
    return argv[0];
})

DEF(nedf_negbinomial, "long negbinomial(long n, double p, long rng?)", "random", "", {
    int rng = argc==3 ? (int)argv[2].dbl : 0;
    argv[0].dbl = negbinomial((int)argv[0].dbl, argv[1].dbl, rng);
    return argv[0];
})

DEF(nedf_poisson, "long poisson(double lambda, long rng?)", "random", "", {
    int rng = argc==2 ? (int)argv[1].dbl : 0;
    argv[0].dbl = poisson(argv[0].dbl, rng);
    return argv[0];
})

//
// Meaningful error for obsolete genk_ functions
//

static double obsolete_genk_function(...)
{
    throw cRuntimeError("Obsolete function -- use the one without the \"genk_\" prefix, and rng as last argument");
}

Define_Function2(genk_uniform, (MathFunc3Args)obsolete_genk_function, 3);
Define_Function2(genk_intuniform, (MathFunc3Args)obsolete_genk_function, 3);
Define_Function2(genk_exponential, (MathFunc2Args)obsolete_genk_function, 2);
Define_Function2(genk_normal, (MathFunc3Args)obsolete_genk_function, 3);
Define_Function2(genk_truncnormal, (MathFunc3Args)obsolete_genk_function, 3);

