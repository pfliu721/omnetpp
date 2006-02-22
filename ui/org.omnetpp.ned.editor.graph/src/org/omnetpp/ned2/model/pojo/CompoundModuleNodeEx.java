package org.omnetpp.ned2.model.pojo;

import java.util.ArrayList;
import java.util.List;

import org.omnetpp.ned2.model.NEDElement;

public class CompoundModuleNodeEx extends CompoundModuleNode {

	public List<SubmoduleNodeEx> getSubmodules() {
		List<SubmoduleNodeEx> result = new ArrayList<SubmoduleNodeEx>();
		for(NEDElement currChild : getFirstSubmodulesChild()) 
			if (currChild instanceof SubmoduleNodeEx) 
				result.add((SubmoduleNodeEx)currChild);
				
		return result;
	}

	public List<ConnectionNodeEx> getConnections() {
		List<ConnectionNodeEx> result = new ArrayList<ConnectionNodeEx>();
		for(NEDElement currChild : getFirstConnectionsChild()) 
			if (currChild instanceof ConnectionNodeEx) 
				result.add((ConnectionNodeEx)currChild);
				
		return result;
	}

}
