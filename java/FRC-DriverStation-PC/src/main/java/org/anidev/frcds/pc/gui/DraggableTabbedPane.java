package org.anidev.frcds.pc.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TabbedPaneUI;

/**
 * JTabbedPane that can be dragged
 */
public class DraggableTabbedPane extends JTabbedPane {
	public static final int DETACH_THRESHOLD=5;
	private volatile boolean dragging=false;
	private volatile boolean draggingForbidden=false;
	private volatile boolean detachingForbidden=false;
	private volatile Rectangle tabBounds=null;
	private volatile BufferedImage tabImage=null;
	private volatile Point currentMouseLocation=null;
	private volatile int draggedTabIndex=0;
	private volatile DropState dropState=null;
	private volatile JWindow dragWindow=null;
	private Map<String,TabInfo> tabInfoList=Collections
			.synchronizedMap(new HashMap<String,TabInfo>());
	private volatile int indicatorPos=-1;
	private List<Listener> listeners=Collections
			.synchronizedList(new ArrayList<Listener>());

	/**
	 * Add mouse listeners
	 */
	public DraggableTabbedPane() {
		super();
		addMouseMotionListener(new DragMotionListener());
		addMouseListener(new DragReleaseListener());
	}

	/**
	 * Figure out the current tab drop state.
	 * @param index the index
	 * @param x x-coordinate
	 * @param y y-coordinate
	 * @return the current drop state
	 */
	public DropState calcTabDropState(int index,int x,int y) {
		if(x>=0&&x<=getWidth()&&y>=tabBounds.y&&y<=tabBounds.y+tabBounds.height) {
			return DropState.ROW_MOVE;
		}
		if(Math.sqrt(Math.pow(x-tabBounds.x,2)+Math.pow(y-tabBounds.y,2))>DETACH_THRESHOLD) {
			return DropState.DETACHED;
		}
		return DropState.NO_CHANGE;
	}

	/**
	 * Set a tab to be draggable or not draggable
	 * @param index the index of the item
	 * @param draggable whether or not the user should be able to drag it
	 */
	public void setTabDraggable(int index,boolean draggable) {
		String title=getTitleAt(index);
		TabInfo info=tabInfoList.get(title);
		if(info==null) {
			info=new TabInfo();
		}
		info.draggable=draggable;
		tabInfoList.put(title,info);
	}

	/**
	 * @param index the index of the item
	 * @return true if that tab is draggable false otherwise
	 */
	public boolean isTabDraggable(int index) {
		String title=getTitleAt(index);
		TabInfo info=tabInfoList.get(title);
		return(info==null?true:info.draggable);
	}

	/**
	 * Set a tab to be detachable or not detachable
	 * @param index the index of the item
	 * @param detachable whether or not that tab can be detached
	 */
	public void setTabDetachable(int index,boolean detachable) {
		String title=getTitleAt(index);
		TabInfo info=tabInfoList.get(title);
		if(info==null) {
			info=new TabInfo();
		}
		info.detachable=detachable;
		tabInfoList.put(title,info);
	}

	/**
	 * @param index the index of the item
	 * @return true if that tab is detachable false otherwise
	 */
	public boolean isTabDetachable(int index) {
		String title=getTitleAt(index);
		TabInfo info=tabInfoList.get(title);
		return(info==null?true:info.detachable);
	}

	/**
	 * @param listener the listener to add
	 */
	public void addTabDragListener(Listener listener) {
		listeners.add(listener);
	}

	/**
	 * @param listener the listener to remove
	 */
	public void removeTabDragListener(Listener listener) {
		listeners.remove(listener);
	}

	/* (non-Javadoc)
	 * @see javax.swing.JTabbedPane#setTitleAt(int, java.lang.String)
	 */
	@Override
	public void setTitleAt(int index,String title) {
		String oldTitle=getTitleAt(index);
		TabInfo info=tabInfoList.get(oldTitle);
		super.setTitleAt(index,title);
		tabInfoList.put(title,info);
	}

	/* (non-Javadoc)
	 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if(indicatorPos<0||!dragging) {
			return;
		}
		int x,y,h;
		Rectangle hoverBounds=getUI().getTabBounds(DraggableTabbedPane.this,
				(indicatorPos>=getTabCount()?indicatorPos-1:indicatorPos));
		x=hoverBounds.x;
		y=hoverBounds.y;
		h=hoverBounds.height;
		if(indicatorPos>=getTabCount()) {
			x+=hoverBounds.width;
		}
		g.setColor(new Color(0,102,255));
		g.fillRect(x-1,y,3,h);
	}

	/**
	 * Listener for tabs with mouse events
	 */
	public static abstract class Listener {
		/**
		 * @param index the index of the item
		 * @param e the mouse event from the dragging of a tab
		 */
		public void tabDragging(int index,MouseEvent e) {
		}

		/**
		 * @param index the index of the item
		 * @param e the mouse event from cancelling dropping a tab
		 */
		public void tabDropCanceled(int index,MouseEvent e) {
		}

		/**
		 * @param oldIndex the old index
		 * @param newIndex the new index
		 * @param e the mouse event from moving a tab
		 */
		public void tabMoved(int oldIndex,int newIndex,MouseEvent e) {
		}

		/**
		 * @param index the index of the item
		 * @param e the mouse event from detaching a tab
		 */
		public void tabDetached(int index,MouseEvent e) {
		}
	}

	/**
	 * The state of a tab drop
	 */
	public static enum DropState {
		NO_CHANGE, // tab dropped back where it came from
		ROW_MOVE, // tab dropped elsewhere on the tabstrip
		DETACHED; // tab dropped far enough away to be detached
	}

	/**
	 * Info if a tab is draggable and detachable
	 */
	private static class TabInfo {
		public boolean draggable=true;
		public boolean detachable=true;
	}

	/**
	 * Listen to the drag motion of the mouse
	 */
	private class DragMotionListener extends MouseMotionAdapter {
		/* (non-Javadoc)
		 * @see java.awt.event.MouseMotionAdapter#mouseDragged(java.awt.event.MouseEvent)
		 */
		@Override
		public void mouseDragged(MouseEvent e) {
			updateStatus(e);
			updateImage(e);
			int oldPos=indicatorPos;
			updateIndicator(e);
			if(oldPos!=indicatorPos) {
				repaint();
			}
		}

		/**
		 * @param e the mouse event that occured
		 */
		public void updateStatus(MouseEvent e) {
			if(!dragging&&!draggingForbidden) {
				// Gets the tab index based on the mouse position
				int tabNumber=getUI().tabForCoordinate(
						DraggableTabbedPane.this,e.getX(),e.getY());
				if(tabNumber>=0) {
					boolean draggable=isTabDraggable(tabNumber);
					boolean detachable=isTabDetachable(tabNumber);
					if(draggable) {
						detachingForbidden=!detachable;
						draggedTabIndex=tabNumber;
						tabBounds=getUI().getTabBounds(
								DraggableTabbedPane.this,tabNumber);
						dragging=true;
						synchronized(listeners) {
							for(Listener listener:listeners) {
								listener.tabDragging(draggedTabIndex,e);
							}
						}
					} else {
						draggingForbidden=true;
					}
				}
			}
		}

		/**
		 * @param e the mouse event that occured
		 */
		public void updateImage(MouseEvent e) {
			if(dragging) {
				// Paint the tabbed pane to a buffer
				BufferedImage totalImage=new BufferedImage(getWidth(),
						getHeight(),BufferedImage.TYPE_INT_ARGB);
				Graphics2D totalGraphics=totalImage.createGraphics();
				totalGraphics.setClip(tabBounds);
				// Don't be double buffered when painting to a static
				// image.
				setDoubleBuffered(false);
				paint(totalGraphics);

				// Paint just the dragged tab to the buffer
				tabImage=new BufferedImage(tabBounds.width,tabBounds.height,
						BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics=tabImage.createGraphics();
				graphics.drawImage(totalImage,0,0,tabBounds.width,
						tabBounds.height,tabBounds.x,tabBounds.y,tabBounds.x
								+tabBounds.width,tabBounds.y+tabBounds.height,
						DraggableTabbedPane.this);

				currentMouseLocation=new Point(e.getXOnScreen(),e
						.getYOnScreen());
				if(detachingForbidden) {
					Point toConvert=new Point(tabBounds.getLocation());
					SwingUtilities.convertPointToScreen(toConvert,
							DraggableTabbedPane.this);
					currentMouseLocation.y=toConvert.y;
					if(e.getX()<0) {
						Point minPoint=new Point(0,0);
						SwingUtilities.convertPointToScreen(minPoint,
								DraggableTabbedPane.this);
						currentMouseLocation.x=minPoint.x;
					} else if(e.getX()+tabBounds.width>getWidth()) {
						Point maxPoint=new Point(getWidth(),0);
						SwingUtilities.convertPointToScreen(maxPoint,
								DraggableTabbedPane.this);
						currentMouseLocation.x=maxPoint.x-tabBounds.width;
					}
				}

				if(dragWindow==null) {
					dragWindow=new JWindow() {
						/* (non-Javadoc)
						 * @see java.awt.Window#paint(java.awt.Graphics)
						 */
						@Override
						public void paint(Graphics g) {
							g.drawImage(tabImage,0,0,null);
						}
					};
					dragWindow.setSize(tabBounds.getSize());
					dragWindow.setVisible(true);
				}
				dragWindow.setLocation(currentMouseLocation);
				dragWindow.repaint();
			}
		}

		/**
		 * @param e the mouse event that occured
		 */
		public void updateIndicator(MouseEvent e) {
			if(!dragging) {
				indicatorPos=-1;
				return;
			}
			int x=e.getX();
			int y=(detachingForbidden?tabBounds.y:e.getY());
			dropState=calcTabDropState(draggedTabIndex,x,y);
			if(detachingForbidden&&dropState==DropState.DETACHED) {
				dropState=DropState.ROW_MOVE;
			}
			indicatorPos=-1;
			if(dropState!=DropState.ROW_MOVE) {
				return;
			}
			TabbedPaneUI ui=getUI();
			int hoverTabIndex=ui.tabForCoordinate(DraggableTabbedPane.this,x,y);
			if(hoverTabIndex<0) {
				Rectangle endTabBounds=ui.getTabBounds(
						DraggableTabbedPane.this,getTabCount()-1);
				if(x<0) {
					indicatorPos=0;
				} else if(x>endTabBounds.x+endTabBounds.width) {
					indicatorPos=getTabCount();
				}
				return;
			}
			Rectangle hoverBounds=ui.getTabBounds(DraggableTabbedPane.this,
					hoverTabIndex);
			indicatorPos=hoverTabIndex;
			if(x>=hoverBounds.x+hoverBounds.width/2.0) {
				indicatorPos++;
			}
		}
	}

	/**
	 * listener for the release of a tab being dragged
	 */
	private class DragReleaseListener extends MouseAdapter {
		/* (non-Javadoc)
		 * @see java.awt.event.MouseAdapter#mouseReleased(java.awt.event.MouseEvent)
		 */
		@Override
		public void mouseReleased(MouseEvent e) {
			draggingForbidden=false;
			detachingForbidden=false;
			if(!dragging) {
				return;
			}
			dragging=false;
			tabImage=null;
			tabBounds=null;
			dragWindow.setVisible(false);
			dragWindow.dispose();
			dragWindow=null;
			boolean moved=false;
			int insertPos=0;
			if(indicatorPos>=0&&indicatorPos!=draggedTabIndex
					&&indicatorPos-1!=draggedTabIndex) {
				Component comp=getComponentAt(draggedTabIndex);
				String title=getTitleAt(draggedTabIndex);
				Icon icon=getIconAt(draggedTabIndex);
				String tooltip=getToolTipTextAt(draggedTabIndex);
				removeTabAt(draggedTabIndex);
				insertPos=indicatorPos;
				if(draggedTabIndex<indicatorPos) {
					insertPos--;
				}
				insertTab(title,icon,comp,tooltip,insertPos);
				setSelectedIndex(insertPos);
				moved=true;
			}
			synchronized(listeners) {
				for(Listener listener:listeners) {
					// Also canceled if ROW_MOVE but did not
					// actually move to a different location
					if(dropState==null||dropState==DropState.NO_CHANGE
							||(dropState==DropState.ROW_MOVE&&!moved)) {
						listener.tabDropCanceled(draggedTabIndex,e);
					} else if(dropState==DropState.ROW_MOVE&&moved) {
						listener.tabMoved(draggedTabIndex,insertPos,e);
					} else if(dropState==DropState.DETACHED) {
						listener.tabDetached(draggedTabIndex,e);
					}
				}
			}
			indicatorPos=-1;
			repaint();
		}
	}
}