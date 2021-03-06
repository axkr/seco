package seco.gui.menu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import seco.gui.common.DialogDescriptor;
import seco.gui.common.DialogDisplayer;
import seco.gui.common.NotifyDescriptor;
import seco.gui.dialog.CellPropsDialog;
import seco.gui.panel.SettingsPreviewPane;
import seco.gui.panel.SyntaxHiliteOptionPane;
import seco.notebook.NotebookUI;
import seco.notebook.style.StyleType;
import seco.notebook.syntax.ScriptSupport;
import seco.notebook.syntax.java.JavaFormatterOptionsPane;
import seco.util.GUIUtil;

public class VisPropsProvider implements DynamicMenuProvider
{
    transient MouseListener mouseListener;

    public VisPropsProvider()
    {
    }

    public boolean updateEveryTime()
    {
        return true;
    }

    public void update(JMenu menu)
    {
        menu.removeAll();
        for (final StyleType s : StyleType.values())
        {
            //TODO: bigger refactoring in NotebookUI is needed
            if(s == StyleType.global) continue;
            JMenuItem item = new JMenuItem(s.getDescription());
            item.addActionListener(new MIActionListener(s));
            menu.add(item);
        }

        JMenuItem item = new JMenuItem("Syntax Styles");
        item.addActionListener(new SyntaxStyleAction());
        menu.add(item);
        item = new JMenuItem("Formatter Properties");
        item.addActionListener(new FormatAction());
        menu.add(item);
    }

    public static final class FormatAction implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            NotebookUI ui = NotebookUI.getFocusedNotebookUI();
            if (ui == null) return;
            JavaFormatterOptionsPane pane = new JavaFormatterOptionsPane();
            SettingsPreviewPane outer = new SettingsPreviewPane(pane);
            DialogDescriptor dd = new DialogDescriptor(
                    GUIUtil.getFrame(ui), outer, "Formatter Properties");
            if (DialogDisplayer.getDefault().notify(dd) == NotifyDescriptor.OK_OPTION)
                outer.save();
        }
    }

    public static class SyntaxStyleAction implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            NotebookUI ui = NotebookUI.getFocusedNotebookUI();
            if (ui == null) return;
            ScriptSupport sup = ui.getDoc().getScriptSupport(
                    ui.getCaretPosition());
            if (sup == null) return;
            SyntaxHiliteOptionPane pane = new SyntaxHiliteOptionPane(sup);
            SettingsPreviewPane outer = new SettingsPreviewPane(ui.getDoc(),
                    pane, null);
            DialogDescriptor dd = new DialogDescriptor(
                    GUIUtil.getFrame(ui), outer, "Syntax Styles");
            if (DialogDisplayer.getDefault().notify(dd) == NotifyDescriptor.OK_OPTION)
            {
                outer.save();
                ui.getDoc().updateStyles();
            }
        }
    }

    public static class MIActionListener extends AbstractAction
    {
        private static final long serialVersionUID = 8167693899124685827L;

        protected StyleType stype;

        public MIActionListener()
        {
        }

        public MIActionListener(StyleType s)
        {
            super(s.toString());
            stype = s;
        }

        public void actionPerformed(ActionEvent evt)
        {
            NotebookUI ui = NotebookUI.getFocusedNotebookUI();
            if (ui == null) return;
            CellPropsDialog dlg = new CellPropsDialog(
                    GUIUtil.getFrame(ui), ui.getDoc(), stype);
            dlg.setSize(440, 330);
            GUIUtil.centerOnScreen(dlg);
            dlg.setVisible(true);
            if (dlg.succeeded())
            {
                ui.revalidate();
                ui.repaint();
            }
        }

        public StyleType getStyleType()
        {
            return stype;
        }

        public void setStyleType(StyleType stype)
        {
            this.stype = stype;
        }
    }
}
