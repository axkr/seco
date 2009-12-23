/*
 * This file is part of the Scriba source distribution. This is free, open-source 
 * software. For full licensing information, please see the LicensingInformation file
 * at the root level of the distribution.
 *
 * Copyright (c) 2006-2007 Kobrix Software, Inc.
 */
package seco.notebook.view;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.NavigationFilter;
import javax.swing.text.Position;
import javax.swing.text.Position.Bias;

import seco.gui.GUIHelper;
import seco.gui.TopFrame;
import seco.notebook.NotebookDocument;
import seco.notebook.NotebookUI;
import seco.notebook.NotebookDocument.UpdateAction;
import seco.notebook.gui.GUIUtilities;
import seco.notebook.html.HTMLEditor;
import seco.things.Cell;
import seco.things.CellGroupMember;
import seco.things.CellUtils;


public class HtmlView extends HidableComponentView
{
    private HTMLEditor view = null;

    public HtmlView(Element element)
    {
        super(element);
    }

    @Override
    public float getAlignment(int axis)
    {
        if (view != null) return 0.0f;
        return super.getAlignment(axis);
    }

    @Override
    public int viewToModel(float x, float y, Shape a, Bias[] biasReturn)
    {
        return getElement().getStartOffset() + 1;
    }

    @Override
    public void setVisible(boolean visible)
    {
        if (view != null) view.setVisible(visible);
        super.setVisible(visible);
    }

    protected Component createComponent()
    {
        if (view == null)
        {
            final NotebookUI ui = (NotebookUI) getContainer();
            String fn = ui.getDoc().getTitle();
            view = new InnerHTMLEditor(fn); // HTMLEditor(fn);
            Cell cell = (Cell) NotebookDocument.getNBElement(getElement());
            view.setContent(CellUtils.getText(cell));
            view.setEditable(!CellUtils.isReadonly(cell));

            SwingUtilities.invokeLater(new Runnable() {
                public void run()
                {
                    ui.getDoc().addAttribute(getElement(),
                            NotebookDocument.ATTR_HTML_EDITOR, view);
                    view.getDoc().addUndoableEditListener(
                            new UndoableEditListener() {
                                public void undoableEditHappened(
                                        UndoableEditEvent e)
                                {
                                    ((NotebookDocument) getElement()
                                            .getDocument()).setModified(true);
                                }
                            });
                }
            });
        }
        return view;
    }

    public class InnerHTMLEditor extends HTMLEditor
    {
        public InnerHTMLEditor(String filename)
        {
            super(filename);
            addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent e)
                {
                    NotebookUI ui = getNotebookUI();
                    if (ui == null) return;
                    ui.getSelectionManager().clearSelections();
                    NotebookUI.setFocusedHTMLEditor(editor);
                    editor = InnerHTMLEditor.this;
                    CellGroupMember cgm = NotebookDocument.getNBElement(getElement());
                    if(CellUtils.isReadonly(cgm))
                        return;
                    TopFrame.getInstance().showHTMLToolBar(true);
                    // System.out.println("InnerHTMLEditor -focusGained: " + ui.getCaretPosition());
                }

                public void focusLost(FocusEvent e)
                {
                    NotebookDocument doc = (NotebookDocument) getElement().getDocument();
                    try{
                     doc.updateCell(getElement(), UpdateAction.syncronize, null);
                    }catch(Exception ex)
                    {
                        ex.printStackTrace();
                    }
                     //System.out.println("InnerHTMLEditor -focusLost: " + e.getOppositeComponent());
                    //TopFrame.getInstance().showHTMLToolBar(false);
                }
            });
            
           addCaretListener(new CaretListener(){

                public void caretUpdate(CaretEvent e)
                {
                    CellGroupMember cgm = NotebookDocument.getNBElement(getElement());
                    if(CellUtils.isReadonly(cgm))  return;
                    GUIHelper.getHTMLToolBar().showAttributes(editor, e.getDot());
                }
                
            });

           setNavigationFilter(new CustomNavigationFilter());
        }

        
        protected PopupListener getPopupListener()
        {
            if (popupListener == null) popupListener = new MyPopupListener();
            return popupListener;
        }

        public Element getElement()
        {
            return HtmlView.this.getElement();
        }

        public NotebookUI getNotebookUI()
        {
            return (NotebookUI) HtmlView.this.getContainer();
        }
        
        public void setCaretPosition(int position) {
            if(position == this.getCaretPosition()) return;
            super.setCaretPosition(position);
        }

        protected class MyPopupListener extends PopupListener
        {
            public void mouseClicked(MouseEvent e)
            {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e))
                {
                    NotebookUI ui = ((InnerHTMLEditor) e.getComponent())
                            .getNotebookUI();
                    if (getPopup().isVisible())
                    {
                        getPopup().setVisible(false);
                    } else
                    {
                        int off = ui.viewToModel(e.getPoint());
                        //getNotebookUI().setCaretPosition(
                        //        getElement().getStartOffset() + 1);
                        if (off != -1) ui.setCaretPosition(off);
                        getPopup().update();
                        Frame f = GUIUtilities.getFrame(e.getComponent());
                        Point pt = getPoint(e, f);
                        getPopup().show(f, pt.x, pt.y);
                    }
                }
            }
            
            private Point getPoint(MouseEvent e, Frame f)
            {
                Point pt = SwingUtilities.convertPoint(getNotebookUI(), e.getX(),
                        e.getY(), f);
                if (e.getComponent() instanceof JComponent)
                    return GUIHelper
                            .computePoint(getNotebookUI(), pt);
                return pt;
            }
        }

        public class CustomNavigationFilter extends NavigationFilter
        {
            private int lastDot;
            private int realDirection;

            public void setDot(NavigationFilter.FilterBypass fb, int dot,
                    Position.Bias bias)
            {
                // special case when caret is on the last line, but not on
                // endpos and down arrow is pressed
                boolean spec_down = realDirection == SwingConstants.SOUTH
                        && lastDot == dot;
//                 System.out.println("InnerHTMLNavigationFilter-setDot: " + dot
//                 + ":" + lastDot
//                 + ":" + getDocument().getLength() + ":" + fb + ":" + bias +
//                 ":" + realDirection);

                InnerHTMLEditor ed = InnerHTMLEditor.this;
                if ((dot == ed.getDocument().getLength() && lastDot == dot)
                        || (dot == 1 && bias == Position.Bias.Backward)
                        || spec_down)
                {
                    boolean up = bias == Position.Bias.Backward;
                    if (spec_down) up = false;
                    int off = (up) ? ed.getElement().getStartOffset() : ed
                            .getElement().getEndOffset();
                    final NotebookUI ui = (NotebookUI) getContainer();
                    ui.setCaretPosition(off + ((up) ? -1 : 1));
                    ui.requestFocus();
                    lastDot = -1;
                    realDirection = 0;
                    return;

                }

                lastDot = dot;
                fb.setDot(dot, bias);
            }

            public int getNextVisualPositionFrom(JTextComponent text, int pos,
                    Position.Bias bias, int direction, Position.Bias[] biasRet)
                    throws BadLocationException
            {
                Position.Bias realBias = (direction == SwingConstants.NORTH || direction == SwingConstants.WEST) ? Position.Bias.Backward
                        : Position.Bias.Forward;
                realDirection = direction;
                biasRet[0] = realBias;
                return super.getNextVisualPositionFrom(text, pos, realBias,
                        direction, biasRet);
            }
        }
    }
 
}
