package org.omnetpp.scave2.editors.providers;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

/**
 * Content provider for a tree built of {@link GenericTreeNode}s.
 * @author andras
 */
public class GenericTreeContentProvider implements ITreeContentProvider {
	
	public Object[] getChildren(Object parentElement) {
		Assert.isTrue(parentElement instanceof GenericTreeNode); // by contract, element MUST be a GenericTreeNode
		GenericTreeNode parent = (GenericTreeNode)parentElement; 
		return parent.getChildren();
	}

	public Object getParent(Object element) {
		Assert.isTrue(element instanceof GenericTreeNode); // by contract, element MUST be a GenericTreeNode
		return ((GenericTreeNode)element).getParent();
	}

	public boolean hasChildren(Object element) {
		return getChildren(element).length > 0;
	}

	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	public void dispose() {
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}
	
}
