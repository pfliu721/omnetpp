package org.omnetpp.scave2.editors.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.omnetpp.common.color.ColorFactory;

public class ChartSheetPage extends ScrolledForm {

	public ChartSheetPage(Composite parent, int style) {
		super(parent, style | SWT.V_SCROLL);
		initialize();
	}
	
	public Composite getChartSheetComposite() {
		return getBody();
	}
	
	public void addChart(Control chart) {
		// set layout data
	}
	
	private void initialize() {
		setExpandHorizontal(true);
		setBackground(ColorFactory.asColor("white"));
		FillLayout layout = new FillLayout(SWT.VERTICAL);
		getBody().setLayout(layout);
	}
}
