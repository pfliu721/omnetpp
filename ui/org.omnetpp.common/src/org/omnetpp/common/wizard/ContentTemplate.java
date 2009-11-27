/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.
  
  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.common.wizard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.omnetpp.common.CommonPlugin;
import org.omnetpp.common.util.LicenseUtils;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.common.wizard.support.FileUtils;
import org.omnetpp.common.wizard.support.LangUtils;
import org.omnetpp.common.wizard.support.ProcessUtils;

import freemarker.cache.StringTemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.log.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModelException;

/**
 * The default implementation of IContentTemplate, using FreeMarker templates.
 * The createCustomPages() method is left undefined, but subclasses could use 
 * XSWTWizardPage for implementing it.
 * 
 * @author Andras
 */
public abstract class ContentTemplate implements IContentTemplate {
    public static final String CONTRIBUTORS_EXTENSIONPOINT_ID = "org.omnetpp.common.wizard.templatecontributors";

    private static final String SETOUTPUT_MARKER = "@@@@ setoutput \"${file}\" @@@@\n";
	private static final String SETOUTPUT_PATTERN = "(?s)@@@@ setoutput \"(.*?)\" @@@@\n"; // filename must be $1
	 
	// template attributes
    private String name;
    private String description;
    private String category;
    private Image image;
    private boolean isDefault;

    static {
        // configure Freemarker to use the Eclipse log (and NOT print to stdout)
        Logger.setLoggerFactory(new FreemarkerEclipseLoggerFactory());
    }
    
    // we need this ClassLoader for the Class.forName() calls in for both FreeMarker and XSWT.
    // Without it, templates (BeanWrapper) and XSWT would only be able to access classes 
    // from the eclipse plug-in from which their code was loaded. In contrast, we want them to
    // have access to classes defined in this plug-in (FileUtils, IDEUtils, etc), and also
    // to the contents of jars loaded at runtime from the user's projects in the workspace.
    // See e.g. freemarker.template.utility.ClassUtil.forName(String)
    private ClassLoader classLoader = null;  
    
    // FreeMarker configuration (stateless)
    private Configuration freemarkerConfiguration = null;

    // contributors list
    private List<IContentTemplateContributor> contributors;
    
    public ContentTemplate() {
    }

    public ContentTemplate(String name, String category, String description) {
        super();
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public ContentTemplate(String name, String category, String description, Image image) {
    	this(name, category, description);
    	this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
    	this.name = name;
    }
    
    public String getCategory() {
    	return category;
    }
    
    public void setCategory(String category) {
    	this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
    	this.description = description;
    }
    
    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
    	this.image = image;
    }

    public boolean getIsDefault() {
        return isDefault;
    }
    
    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public CreationContext createContextFor(IContainer folder, IWizard wizard, String wizardType) {
        // need to install our class loader while createContext runs: pre-registering classes
        // with StaticModel (Math, FileUtils, StringUtils, etc) needs this;
        // see freemarker.template.utility.ClassUtil.forName(String)
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClassLoader());
        try {
            return createContext(folder, wizard, wizardType);
        } 
        finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    protected CreationContext createContext(IContainer folder, IWizard wizard, String wizardType) {
    	CreationContext context = new CreationContext(this, folder, wizard);
    	
    	// pre-register some potentially useful template variables
    	Map<String, Object> variables = context.getVariables();
        variables.put("wizardType", wizardType);
        variables.put("templateName", name);
        variables.put("templateDescription", description);
        variables.put("templateCategory", category);
        variables.put("targetFolder", folder.getFullPath().toString());
        String projectName = folder.getProject().getName();
		variables.put("rawProjectName", projectName);
        variables.put("projectName", projectName);
		variables.put("ProjectName", StringUtils.capitalize(StringUtils.makeValidIdentifier(projectName)));
		variables.put("projectname", StringUtils.lowerCase(StringUtils.makeValidIdentifier(projectName)));
		variables.put("PROJECTNAME", StringUtils.upperCase(StringUtils.makeValidIdentifier(projectName)));
		Calendar cal = Calendar.getInstance();
        variables.put("date", cal.get(Calendar.YEAR)+"-"+cal.get(Calendar.MONTH)+"-"+cal.get(Calendar.DAY_OF_MONTH));
        variables.put("year", ""+cal.get(Calendar.YEAR));
        variables.put("author", System.getProperty("user.name"));
        String licenseCode = LicenseUtils.getDefaultLicense();
        String licenseProperty = licenseCode.equals(LicenseUtils.NONE) ? "" : licenseCode; // @license(custom) is needed, so that wizards knows whether to create banners
        variables.put("licenseCode", licenseProperty); // for @license in package.ned
        variables.put("licenseText", LicenseUtils.getLicenseNotice(licenseCode));
        variables.put("bannerComment", LicenseUtils.getBannerComment(licenseCode, "//"));

        // open access to more facilities
        variables.put("creationContext", context);
        variables.put("classes", BeansWrapper.getDefaultInstance().getStaticModels());
        
        // make Math, FileUtils, StringUtils and static methods of other classes available to the template
        // see chapter "Bean wrapper" in the FreeMarker manual 
        try {
            variables.put("Math", BeansWrapper.getDefaultInstance().getStaticModels().get(Math.class.getName()));
            variables.put("FileUtils", BeansWrapper.getDefaultInstance().getStaticModels().get(FileUtils.class.getName()));
            variables.put("StringUtils", BeansWrapper.getDefaultInstance().getStaticModels().get(StringUtils.class.getName()));
            variables.put("CollectionUtils", BeansWrapper.getDefaultInstance().getStaticModels().get(CollectionUtils.class.getName()));
            variables.put("LangUtils", BeansWrapper.getDefaultInstance().getStaticModels().get(LangUtils.class.getName()));
            variables.put("ProcessUtils", BeansWrapper.getDefaultInstance().getStaticModels().get(ProcessUtils.class.getName()));
        }
        catch (TemplateModelException e1) {
            CommonPlugin.logError(e1);
        }

        // allow contributors to add more vars, register further classes, etc.
        for (IContentTemplateContributor contributor : getContributors()) {
            try {
                contributor.contributeToContext(context);
            }
            catch (Exception e) {
                CommonPlugin.logError("Content template contributor threw an exception", e);
            } 
        }
        return context;
    }
    
    public ClassLoader getClassLoader() {
        if (classLoader == null)
            classLoader = createClassLoader();
        return classLoader;
    }

    protected ClassLoader createClassLoader() {
        // put all contributors' class loaders into the "classpath"
        List<IContentTemplateContributor> contributors = getContributors();
        ClassLoader[] loaders = new ClassLoader[contributors.size()];
        for (int i=0; i<loaders.length; i++)
            loaders[i] = contributors.get(i).getClass().getClassLoader();
        return new DelegatingClassLoader(loaders);
    }

    protected List<IContentTemplateContributor> getContributors() {
        if (contributors == null)
            contributors = createContributors();
        return contributors;
    }

    protected List<IContentTemplateContributor> createContributors() {
        List<IContentTemplateContributor> result = new ArrayList<IContentTemplateContributor>();
        try {
            IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(CONTRIBUTORS_EXTENSIONPOINT_ID);
            for (IConfigurationElement e : config) {
                Assert.isTrue(e.getName().equals("contributor"));
                IContentTemplateContributor contributor = (IContentTemplateContributor) e.createExecutableExtension("class");
                result.add(contributor);
            }
        } catch (Exception ex) {
            CommonPlugin.logError("Error instantiating content template contributors", ex);
        }
        return result;
    }
    
    protected Configuration getFreemarkerConfiguration() {
        if (freemarkerConfiguration == null)
            freemarkerConfiguration = createFreemarkerConfiguration();
        return freemarkerConfiguration;
    }
    
    protected Configuration createFreemarkerConfiguration() {
        // note: subclasses should override to add a real template loader
        Configuration cfg = new Configuration();
        
        cfg.setNumberFormat("computer"); // prevent digit grouping with comma

        // add loader for built-in macro definitions
        final String BUILTINS = "[builtins]";
        cfg.addAutoInclude(BUILTINS);
        String builtins = 
            "<#macro do arg></#macro>" + // allow void methods to be called as: <@do object.setFoo(x)!> 
            "<#macro setoutput file>\n" + 
            SETOUTPUT_MARKER + 
            "</#macro>\n\n";

        for (IContentTemplateContributor contributor : getContributors()) {
            try {
                String code = contributor.getAdditionalTemplateCode();
                if (!StringUtils.isEmpty(code))
                    builtins += "<#-- " + contributor.getClass().getName() + ": -->\n" + code + "\n\n";
            }
            catch (Exception e) {
                CommonPlugin.logError("Content template contributor threw an exception", e);
            } 
        }

        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate(BUILTINS, builtins);
        cfg.setTemplateLoader(loader);

        return cfg;
    }
    
	/**
     * Resolves references to other variables. Should be called from doPerformFinish() before
     * actually processing the templates.
     * 
     * Some variables contain references to other variables (e.g. 
     * "org.example.${projectname}"); substitute them.
     */
	protected void substituteNestedVariables(CreationContext context) throws CoreException {
		Map<String, Object> variables = context.getVariables();
        try {
        	for (String key : variables.keySet()) {
        		Object value = variables.get(key);
        		if (value instanceof String) {
        			String newValue = evaluate((String)value, variables);
        			variables.put(key, newValue);
        		}
        	}
        } catch (Exception e) {
        	throw CommonPlugin.wrapIntoCoreException(e);
        }
	}

	/** 
	 * Performs template processing on the given string, and returns the result.
	 */
	public String evaluate(String value, Map<String, Object> variables) throws TemplateException {
        // classLoader stuff -- see freemarker.template.utility.ClassUtil.forName(String)
	    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClassLoader());
	    
		try {
		    Configuration cfg = getFreemarkerConfiguration();
			int k = 0;
			while (true) {
				Template template = new Template("text" + k++, new StringReader(value), cfg, "utf8");
				StringWriter writer = new StringWriter();
				template.process(variables, writer);
				String newValue = writer.toString();
				if (value.equals(newValue))
					return value;
				value = newValue;
			}
		} 
		catch (IOException e) {
			CommonPlugin.logError(e); 
			return ""; // cannot happen, we work with string reader/writer only
		}
		finally {
	        Thread.currentThread().setContextClassLoader(oldClassLoader);
		}
	}

	
    /**
     * Utility method for performFinish(). Copies a resource into the project,  
     * performing variable substitutions in it. If the template contained 
     * &lt;@setoutput file="..."&gt; tags, multiple files will be saved. 
     */
    protected void createTemplateFile(String containerRelativePath, Configuration freemarkerConfig, String templateName, CreationContext context) throws CoreException {
        createTemplateFile(context.getFolder().getFile(new Path(containerRelativePath)), freemarkerConfig, templateName, context);
    }

    /**
     * Utility method for performFinish(). Copies a resource into the project,  
     * performing variable substitutions in it. If the template contained 
     * &lt;@setoutput file="..."&gt; tags, multiple files will be saved. 
     */
    protected void createTemplateFile(IFile file, Configuration freemarkerConfig, String templateName, CreationContext context) throws CoreException {
        // classLoader stuff -- see freemarker.template.utility.ClassUtil.forName(String)
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClassLoader());

        // substitute variables
    	String content;    	
        try {
        	// perform template substitution
			Template template = freemarkerConfig.getTemplate(templateName, "utf8");
			StringWriter writer = new StringWriter();
			template.process(context.getVariables(), writer);
			content = writer.toString();
		} catch (Exception e) {
			throw CommonPlugin.wrapIntoCoreException(e);
		}
        finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }

		// normalize line endings, remove multiple blank lines, etc
		content = content.replace("\r\n", "\n");
		content = content.replaceAll("\n\n\n+", "\n\n");
		content = content.trim() + "\n";

		// implement <@setoutput file="fname"/> tag: split content to files. "" means the main file
        List<String> chunks = StringUtils.splitPreservingSeparators(content, Pattern.compile(SETOUTPUT_PATTERN));
        Map<String, String> fileContent = new HashMap<String, String>(); // fileName -> content
        fileContent.put("", chunks.get(0));
        for (int i = 1; i < chunks.size(); i += 2) {
            String fileName = chunks.get(i).replaceAll(SETOUTPUT_PATTERN, "$1");
            String chunk = chunks.get(i+1);
            if (!fileContent.containsKey(fileName))
                fileContent.put(fileName, chunk);
            else 
                fileContent.put(fileName, fileContent.get(fileName) + chunk);  // append
        }
		
		// save files (unless blank)
        for (String fileName : fileContent.keySet()) {
            String contentToSave = fileContent.get(fileName);
            if (!StringUtils.isBlank(contentToSave)) {
                // save the file if not blank. Note: we do NOT delete the existing file with 
                // the same name if contentToSave is blank; this is documented behavior.
                IFile fileToSave = null;
                if (fileName.equals(""))
                    fileToSave = file; // main file
                else if (fileName.endsWith("/"))
                    fileToSave = file.getParent().getFile(new Path(fileName+file.getName())); // with main file's name, in a different directory
                else
                    fileToSave = file.getParent().getFile(new Path(fileName));
                createVerbatimFile(fileToSave, new ByteArrayInputStream(contentToSave.getBytes()), context);
            }
        }
    }

    /**
     * Utility method for performFinish(). Creates a file from the given input stream.
     * If the parent folder(s) do not exist, they are created. The project must exist though.
     */
    protected void createVerbatimFile(String containerRelativePath, InputStream inputStream, CreationContext context) throws CoreException {
        createVerbatimFile(context.getFolder().getFile(new Path(containerRelativePath)), inputStream, context);
    }
    
    /**
     * Utility method for performFinish(). Creates a file from the given input stream.
     * If the parent folder(s) do not exist, they are created. The project must exist though.
     */
    protected void createVerbatimFile(IFile file, InputStream inputStream, CreationContext context) throws CoreException {
        if (!file.getParent().exists() && file.getParent() instanceof IFolder)
            createFolder((IFolder)file.getParent(), context);
        try {
            if (!file.exists())
                file.create(inputStream, true, context.getProgressMonitor());
            else if (shouldOverwriteExistingFile(file, context))
                file.setContents(inputStream, true, true, context.getProgressMonitor());
        } catch (Exception e) {
            throw CommonPlugin.wrapIntoCoreException("Cannot create file: "+file.getFullPath().toString(), e);
        }
    }

    /**
     * Called back when a file already exists. Should return true if the file 
     * can be overwritten (otherwise it will be skipped.)
     */
    protected boolean shouldOverwriteExistingFile(final IFile file, final CreationContext context) {
        if (Display.getCurrent() != null) {
            // we are in the UI thread -- ask.
            IWizard wizard = context.getWizard();
            Shell shell = (wizard instanceof IWizard) ? ((Wizard)wizard).getShell() : null;
            return MessageDialog.openQuestion(shell, "Confirm", "File already exists, overwrite?\n" + file.getFullPath().toString());
        }
        else {
            // we are in a background thread -- try in the UI thread
            final boolean result[] = new boolean[1];
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    result[0] = shouldOverwriteExistingFile(file, context);
                }});
            return result[0];
        }
    }
    
    /**
     * Creates a folder, relative to the context resource. If the parent folder(s) 
     * do not exist, they are created. The project must exist though.
     */
    protected void createFolder(String containerRelativePath, CreationContext context) throws CoreException {
        createFolder(context.getFolder().getFolder(new Path(containerRelativePath)), context);
    }

    /**
     * Creates a folder. If the parent folder(s) do not exist, they are created. 
     * The project must exist though.
     */
    protected void createFolder(IFolder folder, CreationContext context) throws CoreException {
        try {
            if (!folder.getParent().exists() && folder.getParent() instanceof IFolder) // if even the project is missing, let folder.create() fail
                createFolder((IFolder)folder.getParent(), context);
            if (!folder.exists())
                folder.create(true, true, context.getProgressMonitor());
        } catch (Exception e) {
            throw CommonPlugin.wrapIntoCoreException("Cannot create folder: "+folder.getFullPath().toString(), e);
        }
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + getName();
    }
}
