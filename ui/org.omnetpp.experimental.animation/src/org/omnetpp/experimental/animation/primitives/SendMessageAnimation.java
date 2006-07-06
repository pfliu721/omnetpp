package org.omnetpp.experimental.animation.primitives;

import org.eclipse.draw2d.Ellipse;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.graphics.Color;
import org.omnetpp.experimental.animation.controller.IAnimationController;
import org.omnetpp.experimental.animation.model.ConnectionId;
import org.omnetpp.figures.ConnectionFigure;

public class SendMessageAnimation extends AbstractAnimationPrimitive {
	private ConnectionId connectionId;

	private double endSimulationTime;	
	
	private Ellipse message;
	
	public SendMessageAnimation(IAnimationController controller,
								double beginSimulationTime,
								double endSimulationTime,
								ConnectionId connectionId) {
		super(controller, beginSimulationTime);
		this.endSimulationTime = endSimulationTime;
		this.connectionId = connectionId;
		this.message = new Ellipse();
		this.message.setForegroundColor(new Color(null, 128, 0, 0));
		this.message.setBackgroundColor(message.getForegroundColor());
	}
	
	@Override
	public double getEndSimulationTime() {
		return endSimulationTime;
	}
	
	public void gotoSimulationTime(double t) {
		if (beginSimulationTime <= t && t <= endSimulationTime) {
			ConnectionFigure connectionFigure = (ConnectionFigure)controller.getFigure(connectionId);

			Point p1 = connectionFigure.getStart();
			Point p2 = connectionFigure.getEnd();
			double alpha = (t - beginSimulationTime) / (endSimulationTime - beginSimulationTime);
			Point p = new Point((1 - alpha) * p1.x + alpha * p2.x, (1 - alpha) * p1.y + alpha * p2.y);
			setConstraint(message, new Rectangle(p.x - 3, p.y - 3, 7, 7));

			showFigure(message);
		}
		else
			hideFigure(message);
	}
}
