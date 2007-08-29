package org.omnetpp.ned.editor.text;

import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry;
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.omnetpp.common.editor.text.NedCompletionHelper;
import org.omnetpp.common.editor.text.TextDifferenceUtils;
import org.omnetpp.common.util.DelayedJob;
import org.omnetpp.common.util.DisplayUtils;
import org.omnetpp.ned.core.NEDResourcesPlugin;
import org.omnetpp.ned.editor.MultiPageNedEditor;
import org.omnetpp.ned.editor.NedEditorPlugin;
import org.omnetpp.ned.editor.text.actions.ConvertToNewFormatAction;
import org.omnetpp.ned.editor.text.actions.DefineFoldingRegionAction;
import org.omnetpp.ned.editor.text.outline.NedContentOutlinePage;
import org.omnetpp.ned.model.INEDElement;
import org.omnetpp.ned.model.ex.NedFileElementEx;
import org.omnetpp.ned.model.notification.INEDChangeListener;
import org.omnetpp.ned.model.notification.NEDMarkerChangeEvent;
import org.omnetpp.ned.model.notification.NEDModelEvent;


/**
 * TODO add documentation
 *
 * @author rhornig
 */
public class TextualNedEditor extends TextEditor implements INEDChangeListener {
	
    private static final String CUSTOM_TEMPLATES_KEY = "org.omnetpp.ned.editor.text.customtemplates";

	private static boolean pushingChanges;

	private static TemplateStore fStore;
    
    /** The context type registry. */
    private static ContributionContextTypeRegistry fRegistry;

    /** The outline page */
	private NedContentOutlinePage fOutlinePage;
	
	/** The projection support */
	private ProjectionSupport fProjectionSupport;
    
	private String lastContent;

	private DelayedJob pullChangesJob;

	/**
	 * Default constructor.
	 */
	public TextualNedEditor() {
		super();
		
		// delay update to avoid concurrent access to document
		pullChangesJob = new DelayedJob(500) {
    		public void run() {
				pullChangesFromNEDResources();
    		}
        };
	}

    /**
     * Returns this plug-in's template store.
     */
    public static TemplateStore getTemplateStore() {
        if (fStore == null) {
            fStore = new ContributionTemplateStore(getContextTypeRegistry(),
            		NedEditorPlugin.getDefault().getPreferenceStore(), CUSTOM_TEMPLATES_KEY);
            try {
                fStore.load();
            } catch (IOException e) {
                NedEditorPlugin.logError(e);
            }
        }
        return fStore;
    }

    /**
     * Returns this plug-in's context type registry.
     */
    public static ContextTypeRegistry getContextTypeRegistry() {
        if (fRegistry == null) {
            // create an configure the contexts available in the template editor
            fRegistry= new ContributionContextTypeRegistry();
            fRegistry.addContextType(NedCompletionHelper.DEFAULT_NED_CONTEXT_TYPE);
        }
        return fRegistry;
    }

	/** The <code>TextualNedEditor</code> implementation of this
	 * <code>AbstractTextEditor</code> method extend the
	 * actions to add those specific to the receiver
	 */
	@Override
    protected void createActions() {
		super.createActions();

		IAction a= new TextOperationAction(NedEditorMessages.getResourceBundle(), "ContentAssistProposal.", this, ISourceViewer.CONTENTASSIST_PROPOSALS);
		a.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
		setAction("ContentAssistProposal", a);

		a= new DefineFoldingRegionAction(NedEditorMessages.getResourceBundle(), "DefineFoldingRegion.", this);
		setAction("DefineFoldingRegion", a);

        a= new ConvertToNewFormatAction(NedEditorMessages.getResourceBundle(), "ConvertToNewFormat.", this);
        setAction("ConvertToNewFormat", a);
	}

	/** The <code>TextualNedEditor</code> implementation of this
	 * <code>AbstractTextEditor</code> method performs any extra
	 * disposal actions required by the ned editor.
	 */
	@Override
    public void dispose() {
		if (fOutlinePage != null)
			fOutlinePage.setInput(null);
        if (pullChangesJob != null)
        	pullChangesJob.cancel();
        //XXX NEDResourcesPlugin.getNEDResources().removeNEDModelChangeListener(this);
		super.dispose();
	}

	/** The <code>TextualNedEditor</code> implementation of this
	 * <code>AbstractTextEditor</code> method performs any extra
	 * revert behavior required by the ned editor.
	 */
	@Override
    public void doRevertToSaved() {
		super.doRevertToSaved();
		if (fOutlinePage != null)
			fOutlinePage.update();
	}

	/** The <code>TextualNedEditor</code> implementation of this
	 * <code>AbstractTextEditor</code> method performs any extra
	 * save behavior required by the ned editor.
	 *
	 * @param monitor the progress monitor
	 */
	@Override
    public void doSave(IProgressMonitor monitor) {
		super.doSave(monitor);
		if (fOutlinePage != null)
			fOutlinePage.update();
	}

	/**
	 * The TextualNedEditor implementation of this AbstractTextEditor
	 * method performs any extra behavior needed by the NED editor.
	 */
	@Override
    public void doSaveAs() {
		super.doSaveAs();
		if (fOutlinePage != null)
			fOutlinePage.update();
	}

	@Override
    public void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		if (fOutlinePage != null)
			fOutlinePage.setInput(input);
		NEDResourcesPlugin.getNEDResources().removeNEDModelChangeListener(this);
		if (input != null)
			NEDResourcesPlugin.getNEDResources().addNEDModelChangeListener(this);
	}

	/**
	 * Sets the content of the text editor to the given string.
	 */
	public void setText(String content) {
		getDocument().set(content);
	}

	/**
	 * Returns the content of the text editor
	 */
	public String getText() {
		return getDocument().get();
	}

    /**
     * Returns the current document under editing
     */
    public IDocument getDocument() {
        return getDocumentProvider().getDocument(getEditorInput());
    }

	protected IFile getFile() {
		return ((IFileEditorInput)getEditorInput()).getFile();
	}

	protected NedFileElementEx getNEDFileModelFromNEDResourcesPlugin() {
		return NEDResourcesPlugin.getNEDResources().getNEDFileModel(getFile());
	}

	/*
	 * @see org.eclipse.ui.texteditor.ExtendedTextEditor#editorContextMenuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
    protected void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);
		addAction(menu, ITextEditorActionConstants.GROUP_EDIT, "ConvertToNewFormat");
		addAction(menu, ITextEditorActionConstants.GROUP_EDIT, "ContentAssistProposal");
		addAction(menu,  ITextEditorActionConstants.GROUP_EDIT, "DefineFoldingRegion");
	}

	/**
	 * The <code>TextualNedEditor</code> implementation of this
	 * <code>AbstractTextEditor</code> method gets
	 * the ned content outline page if request is for an
	 * outline page.
	 *
	 * @param required the required type
	 * @return an adapter for the required type or <code>null</code>
	 */
	@SuppressWarnings("unchecked") @Override
    public Object getAdapter(Class required) {
		if (IContentOutlinePage.class.equals(required)) {
			if (fOutlinePage == null) {
				fOutlinePage= new NedContentOutlinePage(getDocumentProvider(), this);
				if (getEditorInput() != null)
					fOutlinePage.setInput(getEditorInput());
			}
			return fOutlinePage;
		}

		if (fProjectionSupport != null) {
			Object adapter= fProjectionSupport.getAdapter(getSourceViewer(), required);
			if (adapter != null)
				return adapter;
		}

		return super.getAdapter(required);
	}
	
	/* (non-Javadoc)
	 * Method declared on AbstractTextEditor
	 */
	@Override
    protected void initializeEditor() {
		super.initializeEditor();
        //XXX for now NEDResourcesPlugin.getNEDResources().addNEDModelChangeListener(this);
		setSourceViewerConfiguration(new NedSourceViewerConfiguration(this));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.ExtendedTextEditor#createSourceViewer(org.eclipse.swt.widgets.Composite, org.eclipse.jface.text.source.IVerticalRuler, int)
	 */
	@Override
    protected ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
		fAnnotationAccess= createAnnotationAccess();
		fOverviewRuler= createOverviewRuler(getSharedColors());

		ISourceViewer viewer= new ProjectionViewer(parent, ruler, getOverviewRuler(), isOverviewRulerVisible(), styles);
		// ensure decoration support has been created and configured.
		getSourceViewerDecorationSupport(viewer);

		return viewer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.ExtendedTextEditor#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
    public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		ProjectionViewer viewer= (ProjectionViewer) getSourceViewer();
		fProjectionSupport= new ProjectionSupport(viewer, getAnnotationAccess(), getSharedColors());
		fProjectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.error");
		fProjectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.warning");
		fProjectionSupport.install();
		viewer.doOperation(ProjectionViewer.TOGGLE);
        // we should set the selection provider as late as possible because the outer multipage editor overrides it
        // during editor initialization
        // install a selection provider that provides StructuredSelection of NEDModel elements in getSelection
        // instead of ITestSelection
        getSite().setSelectionProvider(new NedSelectionProvider(this));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#adjustHighlightRange(int, int)
	 */
	@Override
    protected void adjustHighlightRange(int offset, int length) {
		ISourceViewer viewer= getSourceViewer();
		if (viewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension= (ITextViewerExtension5) viewer;
			extension.exposeModelRange(new Region(offset, length));
		}
	}

     // TODO: revise do we need it now ???
    /**
     * Marks the current editor content state, so we will be able to detect 
     * changes in the editor.
     *  
     * @see hasContentChanged()
     */
    public void markContent() {
        lastContent = getText();
    }

     // TODO: revise do we need it now ???
    /**
     * Returns whether the content of the editor has changed since the last 
     * markContent() call.
     */
    public boolean hasContentChanged() {
        if (getText() == null)
            return lastContent != null;

        return !(getText().equals(lastContent));
    }

	public boolean isActive() {
		IWorkbenchPage activePage = getSite().getWorkbenchWindow().getActivePage(); // may be null during startup
		IEditorPart activeEditorPart = activePage==null ? null : activePage.getActiveEditor();
		return activeEditorPart == this ||
			(activeEditorPart instanceof MultiPageNedEditor && ((MultiPageNedEditor)activeEditorPart).isActiveEditor(this));
	}

    public void modelChanged(NEDModelEvent event) {
    	if (event.getSource() != null && !(event instanceof NEDMarkerChangeEvent) && !isActive() && !pushingChanges) { //XXX looks like sometimes this condition is not enough!
			INEDElement nedFileElement = event.getSource().getContainingNedFileElement();

			if (nedFileElement == getNEDFileModelFromNEDResourcesPlugin())
				pullChangesJob.restartTimer();
    	}
    }

	public synchronized void pushChangesIntoNEDResources() {
		pushChangesIntoNEDResources(true);
	}
    
	/**
	 * Pushes down text changes from document into NEDResources.
	 */
	public synchronized void pushChangesIntoNEDResources(final boolean evenIfEditorIsInactive) {
		DisplayUtils.runNowOrAsyncInUIThread(new Runnable() {
			public synchronized void run() {
				Assert.isTrue(!pushingChanges);
				if (evenIfEditorIsInactive || isActive()) {
					try {
						// this must be static, because we may have several text editors open
						// (via Window|New Editor) which are internally synced to each other 
						// by the platform -- so we need to block *all* reconcilers from running.
						// Being static causes no problems with multiple reconcilers, because 
						// the access is serialized through asyncExec.
						pushingChanges = true;
						// perform parsing (of full text, we ignore the changed region)
						NEDResourcesPlugin.getNEDResources().setNEDFileText(getFile(), getText());
					}
					finally {
						pushingChanges = false;
					}
				}
			}
		});
	}
	
	/**
	 * Pulls changes from NEDResources and applies to document as text changes.
	 */
	public synchronized void pullChangesFromNEDResources() {
		pullChangesJob.cancel();
		DisplayUtils.runNowOrAsyncInUIThread(new Runnable() {
			public synchronized void run() {
				Assert.isTrue(Display.getCurrent() != null);
				System.out.println("texteditor: pulling changes from NEDResources");
				TextDifferenceUtils.modifyTextEditorContentByApplyingDifferences(
						getDocument(), getNEDFileModelFromNEDResourcesPlugin().getNEDSource());
				//XXX then parse in again, and update line numbers with the resulting tree?
			}
		});
	}
}
