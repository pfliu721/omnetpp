package org.omnetpp.inifile.editor.form;

import static org.omnetpp.inifile.editor.model.ConfigurationRegistry.CFGID_DESCRIPTION;
import static org.omnetpp.inifile.editor.model.ConfigurationRegistry.CFGID_NETWORK;
import static org.omnetpp.inifile.editor.model.ConfigurationRegistry.CONFIG_;
import static org.omnetpp.inifile.editor.model.ConfigurationRegistry.EXTENDS;
import static org.omnetpp.inifile.editor.model.ConfigurationRegistry.GENERAL;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.emf.edit.ui.dnd.LocalTransfer;
import org.eclipse.emf.edit.ui.dnd.ViewerDragAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.omnetpp.common.image.ImageFactory;
import org.omnetpp.common.ui.ActionContributionItem2;
import org.omnetpp.common.ui.GenericTreeContentProvider;
import org.omnetpp.common.ui.GenericTreeLabelProvider;
import org.omnetpp.common.ui.GenericTreeNode;
import org.omnetpp.common.ui.GenericTreeUtils;
import org.omnetpp.common.ui.IHoverTextProvider;
import org.omnetpp.inifile.editor.InifileEditorPlugin;
import org.omnetpp.inifile.editor.actions.AddInifileKeysAction;
import org.omnetpp.inifile.editor.editors.InifileEditor;
import org.omnetpp.inifile.editor.model.IInifileDocument;
import org.omnetpp.inifile.editor.model.InifileHoverUtils;
import org.omnetpp.inifile.editor.model.InifileUtils;

/**
 * Inifile editor page to manage the sections in the file.
 * 
 * @author Andras
 */
public class SectionsPage extends FormPage {
	private static final Image ICON_ERROR = InifileEditorPlugin.getCachedImage("icons/full/obj16/Error.png");
	private static final String CIRCLE_WARNING_TEXT = "NOTE: Sections that form circles (which is illegal) are not displayed here -- switch to text mode to fix them!";
	private static final String HINT_TEXT = "\nDrag and drop sections to edit the fallback chains of parameter and configuration lookups.";

	private Label label;
	private TreeViewer treeViewer;
	
	private IAction addAction = new Action("New...") {
		public void run() {
			createNewSection();
		}	
	};
	private IAction editAction = new Action("Edit...") {
		public void run() {
			editSelectedSection();
		}	
	};
	private IAction removeAction = new Action("Remove") {
		public void run() {
			removeSelectedSection();
		}	
	};
	private IAction gotoParametersAction = new Action("Go To Parameters") {
		public void run() {
			gotoSectionParameters();
		}	
	};
	private IAction addKeysAction = new AddInifileKeysAction();

	static class SectionData {
		String sectionName;
		boolean hasError;

		public SectionData(String sectionName, boolean isUndefined) {
			this.sectionName = sectionName;
			this.hasError = isUndefined;
		}

		/* label provider maps to this */
		@Override
		public String toString() {
			return sectionName + (hasError ? " (the section it extends does not exist)" : "");
		}

		/* needed for treeViewer.setSelection() to work */
		@Override
		public boolean equals(Object obj) {
			if (obj==null || getClass()!=obj.getClass())
				return false;
			return sectionName.equals(((SectionData)obj).sectionName);
		}
	}

	public SectionsPage(Composite parent, InifileEditor inifileEditor) {
		super(parent, inifileEditor);

		// layout: 2x2 grid: (label, dummy) / (tree, buttonGroup)
		setLayout(new GridLayout(2,false));
		((GridLayout)getLayout()).marginRight = RIGHT_MARGIN;

		// create title
		Composite titleArea = createTitleArea(this, "Sections");
		((GridData)titleArea.getLayoutData()).horizontalSpan = 2;

		// create section chain label
		label = new Label(this, SWT.NONE);
		label.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		((GridData)label.getLayoutData()).horizontalSpan = 2;
		label.setText(HINT_TEXT);

		// create treeviewer and buttons
		treeViewer = createAndConfigureTreeViewer();
		treeViewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite buttonGroup = createButtons();
		buttonGroup.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, true));

		reread();
	}

	private TreeViewer createAndConfigureTreeViewer() {
		final TreeViewer treeViewer = new TreeViewer(this, SWT.MULTI | SWT.BORDER);
		treeViewer.setLabelProvider(new GenericTreeLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				String section = getSectionNameFromTreeNode(element);
				String networkName = getInifileDocument().getValue(section, CFGID_NETWORK.getKey());
				String description = getInifileDocument().getValue(section, CFGID_DESCRIPTION.getKey());
				String text = section;
				if (networkName != null)
					text += " (network: " + networkName+")";
				if (description != null)
					text += " -- " + description;
				return text;
			}
			@Override
			public Image getImage(Object element) {
				if (element instanceof GenericTreeNode)
					element = ((GenericTreeNode)element).getPayload();
				if (element instanceof SectionData) {
					SectionData payload = (SectionData) element;
					return payload.hasError ? ICON_ERROR : ImageFactory.getImage(ImageFactory.MODEL_IMAGE_FOLDER); //XXX cache icon!
				}
				return null;
			}
		}));
		treeViewer.setContentProvider(new GenericTreeContentProvider());

		// drag and drop support
		setupDragAndDropSupport(treeViewer);

		// on double-click, show section in the parameters page
		treeViewer.getTree().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent event) {
				gotoParametersAction.run();
			}
		});

		// export the tree's selection as editor selection
		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateActions();
				IStructuredSelection sel = (IStructuredSelection) event.getSelection();
				String section = getSectionNameFromTreeNode(sel.getFirstElement());
				if (section != null)
					setEditorSelection(section, null);
			}
		});

		// add tooltip support
		addTooltipSupport(treeViewer.getTree(), new IHoverTextProvider() {
			public String getHoverTextFor(Control control, int x, int y) {
				Item item = treeViewer.getTree().getItem(new Point(x,y));
				String section = getSectionNameFromTreeNode(item==null ? null : item.getData());
				return section==null ? null : InifileHoverUtils.getSectionHoverText(section, getInifileDocument(), getInifileAnalyzer());
			}
		});

		// add context menu
		MenuManager menuManager = new MenuManager();
		menuManager.add(addAction);
		menuManager.add(editAction);
		menuManager.add(removeAction);
		menuManager.add(new Separator());
		menuManager.add(gotoParametersAction);
		menuManager.add(addKeysAction);
		treeViewer.getTree().setMenu(menuManager.createContextMenu(treeViewer.getTree()));
		
		return treeViewer;
	}

	protected void updateActions() {
		ISelection selection = treeViewer.getSelection();
		editAction.setEnabled(!selection.isEmpty());
		removeAction.setEnabled(!selection.isEmpty());
		gotoParametersAction.setEnabled(!selection.isEmpty());
		addKeysAction.setEnabled(!selection.isEmpty());
	}

	private void setupDragAndDropSupport(TreeViewer viewer) {
		int dndOperations = DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK;
		Transfer[] transfers = new Transfer[] { LocalTransfer.getInstance() };
		viewer.addDragSupport(dndOperations, transfers, new ViewerDragAdapter(viewer));
		viewer.addDropSupport(dndOperations, transfers, new DropTargetAdapter() {
			public void drop(DropTargetEvent event) {
				// note: in theory, the user can drag across different treeViewers
				// (i.e. from a different inifile editor, or even from a Scave editor!), 
				// so we have to be careful when looking at the dragged data 
				String[] draggedSections = getSectionNamesFromTreeSelection((IStructuredSelection) event.data);
				String targetSectionName = getSectionNameFromTreeNode(event.item==null ? null : event.item.getData());

				if (draggedSections.length != 0 && targetSectionName != null) {
					sectionsDragged(draggedSections, targetSectionName);
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	protected static String[] getSectionNamesFromTreeSelection(ISelection selection) {
		ArrayList list = new ArrayList();
		if (selection instanceof IStructuredSelection) {
			for (Object item : ((IStructuredSelection)selection).toArray()) {
				String payload = getSectionNameFromTreeNode(item);
				if (payload != null)
					list.add(payload);
			}
		}
		return (String[]) list.toArray(new String[]{});
	}

	protected static String getSectionNameFromTreeNode(Object data) {
		if (data instanceof GenericTreeNode)
			data = ((GenericTreeNode)data).getPayload();
		if (data instanceof SectionData)
			return ((SectionData) data).sectionName;
		return null;
	}

	protected void setSectionExtendsKey(String sectionName, String extendsSectionName) {
		IInifileDocument doc = getInifileDocument();
		if (extendsSectionName.equals(GENERAL))
			doc.removeKey(sectionName, EXTENDS);
		else {
			String value = extendsSectionName.replaceAll("^Config +", "");
			if (doc.containsKey(sectionName, EXTENDS))
				doc.setValue(sectionName, EXTENDS, value);
			else
				InifileUtils.addEntry(doc, sectionName, EXTENDS, value, null);
		}
	}

	protected Composite createButtons() {
		Composite buttonGroup = new Composite(this, SWT.NONE);
		buttonGroup.setLayout(new GridLayout(1,false));
		addActionButton(buttonGroup, addAction);
		addActionButton(buttonGroup, editAction);
		addActionButton(buttonGroup, removeAction);
		return buttonGroup;
	}

	protected static void addActionButton(Composite buttonGroup, IAction action) {
		Button button = new ActionContributionItem2(action, ActionContributionItem.MODE_FORCE_TEXT).fill2(buttonGroup);
		button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
	}

	/**
	 * Invoked by clicking on the "New Section" button
	 */
	protected void createNewSection() {
		// create and configure dialog
		IInifileDocument doc = getInifileDocument();
		SectionDialog dialog = new SectionDialog(getShell(), "New Section", "Create new section", doc, null);
		dialog.setSectionName("New");
		String[] selection = getSectionNamesFromTreeSelection(treeViewer.getSelection());
		if (selection.length != 0)
			dialog.setExtendsSection(selection[0]);

		// open the dialog
		if (dialog.open()==Window.OK) {
			try {
				String sectionName = dialog.getSectionName();
				InifileUtils.addSection(doc, sectionName);

				String description = dialog.getDescription();
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, CFGID_DESCRIPTION.getKey(), description.equals("") ? null : description);			

				String extendsSection = dialog.getExtendsSection();
				String extendsName = extendsSection.equals(GENERAL) ? null : extendsSection.replaceFirst(CONFIG_, "");
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, EXTENDS, extendsName);			

				String networkName = dialog.getNetworkName();
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, CFGID_NETWORK.getKey(), networkName.equals("") ? null : networkName);			

				reread();
			}
			catch (RuntimeException e) {
				showErrorDialog(e);
			}
		}
	}

	/**
	 * Invoked by clicking on the "Edit Section" button
	 */
	protected void editSelectedSection() {
		// create and configure dialog
		String[] selection = getSectionNamesFromTreeSelection(treeViewer.getSelection());
		if (selection.length == 0)
			return;
		String oldSectionName = selection[0];
		IInifileDocument doc = getInifileDocument();
		SectionDialog dialog = new SectionDialog(getShell(), "Rename/Edit Section", "Rename or edit section ["+oldSectionName+"]", doc, oldSectionName);

		// open the dialog
		if (dialog.open()==Window.OK) {
			try {
				String sectionName = dialog.getSectionName();
				if (!sectionName.equals(oldSectionName))
					InifileUtils.renameSection(doc, oldSectionName, sectionName);

				String description = dialog.getDescription();
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, CFGID_DESCRIPTION.getKey(), description.equals("") ? null : description);			

				String extendsSection = dialog.getExtendsSection();
				String extendsName = extendsSection.equals(GENERAL) ? null : extendsSection.replaceFirst(CONFIG_, "");
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, EXTENDS, extendsName);			

				String networkName = dialog.getNetworkName();
				InifileUtils.addOrSetOrRemoveEntry(doc, sectionName, CFGID_NETWORK.getKey(), networkName.equals("") ? null : networkName);			

				reread();
			}
			catch (RuntimeException e) {
				showErrorDialog(e);
			}
		}
	}

	/**
	 * Invoked by clicking on the "Remove Section" button
	 */
	protected void removeSelectedSection() {
		try {
			String[] selection = getSectionNamesFromTreeSelection(treeViewer.getSelection());
			if (selection.length != 0) {
				boolean ok = MessageDialog.openQuestion(getShell(), "Delete Sections", "Do you want to delete the selected "+(selection.length==1 ? "section" : selection.length+" sections")+"?");
				if (ok) {
					for (String sectionName : selection)
						getInifileDocument().removeSection(sectionName);
					reread();
				}
			}
		}
		catch (RuntimeException e) {
			showErrorDialog(e);
		}
	}

	/**
	 * Goes to the Parameters page of the inifile editor and chooses the selection section.
	 * Invoked by double-clicking the tree viewer.
	 */
	protected void gotoSectionParameters() {
		String[] selection = getSectionNamesFromTreeSelection(treeViewer.getSelection());
		if (selection.length != 0) {
			String section = selection[0];
			InifileFormEditor formEditor = getEditorData().getInifileEditor().getFormEditor();
			formEditor.showCategoryPage(InifileFormEditor.PARAMETERS_PAGE);
			formEditor.getFormPage().gotoSection(section);
		}
	}
	
	/**
	 * Invoked when the user selects a few sections, and drags them to another section.
	 */
	protected void sectionsDragged(String[] draggedSections, String targetSectionName) {
		try {
			//System.out.println(draggedSections.length + " items dropped to: "+targetSectionName);
			IInifileDocument doc = getInifileDocument();
			for (String draggedSectionName : draggedSections)
				if (getInifileDocument().containsSection(draggedSectionName)) // might occur if it was dragged from a different editor's treeviewer...
					if (!InifileUtils.sectionChainContains(doc, targetSectionName, draggedSectionName)) // avoid circles
						setSectionExtendsKey(draggedSectionName, targetSectionName);
			reread();
		}
		catch (RuntimeException e) {
			showErrorDialog(e);
		}
	}

	@Override
	public boolean setFocus() {
		return treeViewer.getTree().setFocus();
	}

	@Override
	public String getPageCategory() {
		return InifileFormEditor.SECTIONS_PAGE;
	}

	@Override
	public void reread() {
		super.reread();
		IInifileDocument doc = getInifileDocument();

		// create root node
		GenericTreeNode rootNode = new GenericTreeNode("root");
		GenericTreeNode generalSectionNode = new GenericTreeNode(new SectionData(GENERAL, false));
		rootNode.addChild(generalSectionNode);

		// handling circles: they won't appear in the tree (property of the tree
		// building algorithm), and we warn the user (in the label text, see below)
		label.setText(!getInifileAnalyzer().containsSectionCircles() ? HINT_TEXT : HINT_TEXT + "\n" + CIRCLE_WARNING_TEXT);
		layout(true);  // number of lines in label may have changed

		// build tree
		HashMap<String,GenericTreeNode> nodes = new HashMap<String, GenericTreeNode>();
		for (String sectionName : doc.getSectionNames()) {
			if (sectionName.startsWith(CONFIG_)) {
				GenericTreeNode node = getOrCreate(nodes, sectionName, false);
				String extendsName = doc.getValue(sectionName, EXTENDS);
				if (extendsName == null) {
					// no "extends=...": falls back to [General]
					generalSectionNode.addChild(node);
				}
				else {
					// add as child to the section it extends
					String extendsSectionName = CONFIG_+extendsName;
					if (doc.containsSection(extendsSectionName)) {
						GenericTreeNode extendsSectionNode = getOrCreate(nodes, extendsSectionName, false);
						extendsSectionNode.addChild(node);
					}
					else {
						// "extends=...": is bogus, fall back to [General]
						((SectionData)(node.getPayload())).hasError = true;
						generalSectionNode.addChild(node);
					}
				}
			}
		}

		// reduce flicker: only overwrite existing tree input if it's not the same as this one
		if (treeViewer.getInput()==null || !GenericTreeUtils.treeEquals(rootNode, (GenericTreeNode)treeViewer.getInput())) {
			treeViewer.setInput(rootNode);
			treeViewer.expandAll();
		}

		treeViewer.refresh();  // refresh labels anyway
		updateActions();
	}

	private GenericTreeNode getOrCreate(HashMap<String, GenericTreeNode> nodes, String sectionName, boolean isUndefined) {
		GenericTreeNode node = nodes.get(sectionName);
		if (node==null) {
			node = new GenericTreeNode(new SectionData(sectionName, isUndefined));
			nodes.put(sectionName, node);
		}
		return node;
	}

	@Override
	public void gotoSection(String section) {
		treeViewer.setSelection(new StructuredSelection(new GenericTreeNode(new SectionData(section, false))));
	}

	@Override
	public void gotoEntry(String section, String key) {
		gotoSection(section);
	}

}
