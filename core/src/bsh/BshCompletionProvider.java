/*
 * This file is part of the Scriba source distribution. This is free, open-source 
 * software. For full licensing information, please see the LicensingInformation file
 * at the root level of the distribution.
 *
 * Copyright (c) 2006-2007 Kobrix Software, Inc.
 */
package bsh;

import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JToolTip;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import seco.notebook.NotebookDocument;
import seco.notebook.syntax.ScriptSupport;
import seco.notebook.syntax.completion.AsyncCompletionQuery;
import seco.notebook.syntax.completion.AsyncCompletionTask;
import seco.notebook.syntax.completion.BaseAsyncCompletionQuery;
import seco.notebook.syntax.completion.Completion;
import seco.notebook.syntax.completion.CompletionDocumentation;
import seco.notebook.syntax.completion.CompletionProvider;
import seco.notebook.syntax.completion.CompletionResultSet;
import seco.notebook.syntax.completion.CompletionTask;
import seco.notebook.syntax.completion.CompletionU;
import seco.notebook.syntax.completion.JavaDocManager;
import seco.notebook.syntax.completion.MethodParamsTipPaintComponent;
import seco.notebook.syntax.java.JavaResultItem;
import seco.util.DocumentUtilities;

public class BshCompletionProvider implements CompletionProvider
{
    public int getAutoQueryTypes(JTextComponent component, String typedText)
    {
        if (".".equals(typedText)) // &&
            // !sup.isCompletionDisabled(component.getCaret().getDot()))
            return COMPLETION_QUERY_TYPE;
        if (" ".equals(typedText)) return TOOLTIP_QUERY_TYPE;
        return 0;
    }

    public CompletionTask createTask(int queryType, JTextComponent component)
    {
        int offset = component.getCaret().getDot();
        ScriptSupport sup = ((NotebookDocument) component.getDocument())
                .getScriptSupport(offset);
        if (sup.isCommentOrLiteral(offset - 1)) return null;
        if (queryType == COMPLETION_QUERY_TYPE) return new AsyncCompletionTask(
                new Query(component.getCaret().getDot()), component);
        else if (queryType == DOCUMENTATION_QUERY_TYPE) return new AsyncCompletionTask(
                new DocQuery(null), component);
        else if (queryType == TOOLTIP_QUERY_TYPE)
            return new AsyncCompletionTask(new ToolTipQuery(), component);
        return null;
    }

    static final class Query extends BaseAsyncCompletionQuery
    {
        public Query(int caretOffset)
        {
            super(caretOffset);
        }

        protected void query(CompletionResultSet resultSet,
                NotebookDocument doc, int offset)
        {
            ScriptSupport sup = doc.getScriptSupport(offset);
            queryCaretOffset = offset;
            queryAnchorOffset = offset;
            BshAst p = (BshAst) sup.getParser();
            try
            {
                String s = sup.getCommandBeforePt(offset);
                Object obj = p.resolveVar(s, offset);
                if (obj == null)
                {
                    resultSet.finish();
                    return;
                }
                Class<?> cls = obj.getClass();
                if (cls.getName().indexOf("bsh.XThis") >= 0)
                {
                    populateXThisRef(resultSet, obj);
                }
                if (obj instanceof CompletionU.DBPackageInfo)
                {
                    CompletionU.populatePackage(resultSet,
                            (CompletionU.DBPackageInfo) obj, queryCaretOffset);
                    queryResult = resultSet;
                    resultSet.finish();
                    return;
                }
                int mod = Modifier.PUBLIC;
                if (cls.getName().equals(ClassIdentifier.class.getName()))
                {
                    cls = BshAst.getClsFromClassIdentifier(obj);
                    mod = Modifier.STATIC;
                }
                if (p.isPrivateAccessAllowed()) mod |= Modifier.PRIVATE;
                if (!p.evaled_or_guessed) cls = (Class<?>) obj;
                CompletionU
                        .populateClass(resultSet, cls, mod, queryCaretOffset);
                queryResult = resultSet;
            }
            catch (Exception ex)
            {
                // stay silent on eval error
                // if (!(ex instanceof UtilEvalError || ex instanceof EvalError)
                // || ex instanceof ScriptException) ex.printStackTrace();
            }
            resultSet.finish();
        }

        private static final Object[] empty = new Object[0];
        private static final String REGEX = "[a-zA-Z_]+[a-zA-Z0-9_]*";

        private void populateXThisRef(CompletionResultSet resultSet, Object t)
                throws UtilEvalError
        {
            try
            {
                Object ns = t.getClass().getMethod("getNameSpace").invoke(t,
                        empty);
                String[] var_names = (String[]) ns.getClass().getMethod(
                        "getVariableNames").invoke(ns, empty);
                Method method = ns.getClass().getMethod("getVariable",
                        new Class[] { String.class });
                for (int i = 0; i < var_names.length; i++)
                {
                    if (!var_names[i].matches(REGEX)) continue;
                    Object obj = method.invoke(ns,
                            new Object[] { var_names[i] });
                    Class<?> cls = (obj instanceof Primitive) ? ((Primitive) obj)
                            .getType()
                            : obj.getClass();
                    if (cls.getName().startsWith("jsint.")) continue;
                    // System.out.println("XThis - vars: " + var_names[i] + ":"
                    // + cls.getName());
                    JavaResultItem item = new JavaResultItem.VarResultItem(
                            var_names[i], cls, Modifier.PUBLIC);
                    item.setSubstituteOffset(queryCaretOffset);
                    resultSet.addItem(item);
                }
                Object[] ms = (Object[]) ns.getClass().getMethod("getMethods")
                        .invoke(ns, empty);

                for (int i = 0; i < ms.length; i++)
                {
                    String name = (String) ms[i].getClass()
                            .getMethod("getName").invoke(ms[i], empty);
                    if (!name.matches(REGEX)) continue;
                    Class<?> retType = (Class<?>) ms[i].getClass().getMethod(
                            "getReturnType").invoke(ms[i], empty);
                    Class<?>[] params = (Class[]) ms[i].getClass().getMethod(
                            "getParameterTypes").invoke(ms[i], empty);
                    // System.out.println("XThis - methods: " + name + ":" +
                    // ((retType != null) ? retType.getName(): "null"));
                    JavaResultItem item = new JavaResultItem.MethodItem(name,
                            retType, params, null);
                    item.setSubstituteOffset(queryCaretOffset);
                    resultSet.addItem(item);
                }
                queryResult = resultSet;
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public static class DocQuery extends AsyncCompletionQuery
    {
        private Object item;
        private JTextComponent component;
        private static Action goToSource = new AbstractAction() {
            public void actionPerformed(ActionEvent e)
            {
                // DocItem doc = (DocItem)e.getSource();
                // ???JMIUtils.openElement((Element)doc.item);
                if (e != null)
                {
                    Completion.get().hideDocumentation();
                }
            }
        };

        public DocQuery(Object item)
        {
            this.item = item;
        }

        protected void query(CompletionResultSet resultSet,
                NotebookDocument doc, int caretOffset)
        {
            if (item != null && JavaDocManager.SHOW_DOC)
            {
                resultSet.setDocumentation(new DocItem(
                        getAssociatedObject(item), null));
            }
            resultSet.finish();
        }

        protected void prepareQuery(JTextComponent component)
        {
            this.component = component;
        }

        private Object getAssociatedObject(Object item)
        {
            Object ret = item;
            if (item instanceof JavaResultItem)
            {
                ret = ((JavaResultItem) item).getAssociatedObject();
            }
            return ret;
        }

        private class DocItem implements CompletionDocumentation
        {
            private String text;
            private JavaDoc javaDoc;
            private Object item;
            private URL url;

            public DocItem(Object item, JavaDoc javaDoc)
            {
                this.javaDoc = javaDoc != null ? javaDoc : new JavaDoc(this,
                        item);
                this.url = getURL(item);
            }

            public CompletionDocumentation resolveLink(String link)
            {
                return null;
            }

            public String getText()
            {
                return text;
            }

            public URL getURL()
            {
                return url;
            }

            private URL getURL(Object item)
            {
                return javaDoc.getURL(item);
            }

            public Action getGotoSourceAction()
            {
                return item != null ? goToSource : null;
            }

            private class JavaDoc
            {
                public static final String CONTENT_NOT_FOUND = "JavaDoc Not Found.";
                private DocItem docItem;

                private JavaDoc(DocItem docItem, Object item)
                {
                    this.docItem = docItem;
                    showJavaDoc(JavaDocManager.getInstance().getHTML(item));
                }

                private URL getURL(Object item)
                {
                    return null;
                }

                protected void showJavaDoc(String preparedText)
                {
                    if (preparedText == null) preparedText = CONTENT_NOT_FOUND;
                    docItem.text = preparedText;
                }
            }
        }
    }

    static class ToolTipQuery extends AsyncCompletionQuery
    {
        private JTextComponent component;
        private int queryCaretOffset;
        private int queryAnchorOffset;
        private JToolTip queryToolTip;
        /**
         * Method/constructor '(' position for tracking whether the method is
         * still being completed.
         */
        private Position queryMethodParamsStartPos = null;
        private boolean otherMethodContext;

        protected void query(CompletionResultSet resultSet,
                NotebookDocument doc, int caretOffset)
        {
            queryMethodParamsStartPos = null;
            ScriptSupport sup = doc.getScriptSupport(caretOffset);
            if (sup == null || !(sup.getParser() instanceof BshAst)) return;
            BshAst p = (BshAst) sup.getParser();
            if (p.getRootNode() == null)
            {
                resultSet.finish();
                return;
            }
            SimpleNode n = ParserUtils.getASTNodeAtOffset(sup.getElement(), p
                    .getRootNode(), caretOffset - 1);
            SimpleNode outer = n;
            if (outer != null)
                outer = ParserUtils.getParentOfType(n,
                        BSHMethodInvocation.class);
            if (outer == null)
                outer = ParserUtils.getParentOfType(n, BSHPrimarySuffix.class);
            if (outer == null)
            {
                resultSet.finish();
                return;
            }
            int offset = sup.lineToOffset(outer.lastToken.endLine - 1,
                    outer.lastToken.endColumn) + 2;
            Object obj = p.resolveVar(sup.getCommandBeforePt(offset), offset);
            if (obj != null && obj instanceof Method)
            {
                List<List<String>> list = new ArrayList<List<String>>();
                populateResult(list, ((Method)obj).getDeclaringClass(), 
                        ((Method)obj).getName());
                resultSet
                .setToolTip(queryToolTip = new MethodParamsTipPaintComponent(
                        list, -1));
            }
           
            resultSet.finish();
        }

        private void populateResult(List<List<String>> list, Class<?> cls, String name)
        {
            Method[] ms = cls.getMethods();
            for (int i = 0; i < ms.length; i++)
            {
                //if (ms[i].getModifiers() != modifiers) continue;
                if (!ms[i].getName().equals(name)) continue;
                JavaResultItem item = new JavaResultItem.MethodItem(ms[i]);
                List<String> parms = new ArrayList<String>();
                parms.add(item.toString());
                list.add(parms);
            }
        }

        protected void prepareQuery(JTextComponent component)
        {
            this.component = component;
        }

        protected boolean canFilter(JTextComponent component)
        {
            CharSequence text = null;
            int textLength = -1;
            int caretOffset = component.getCaretPosition();
            Document doc = component.getDocument();
            try
            {
                if (caretOffset - queryCaretOffset > 0) text = DocumentUtilities
                        .getText(doc, queryCaretOffset, caretOffset
                                - queryCaretOffset);
                else if (caretOffset - queryCaretOffset < 0) text = DocumentUtilities
                        .getText(doc, caretOffset, queryCaretOffset
                                - caretOffset);
                else
                    textLength = 0;
            }
            catch (BadLocationException e)
            {
            }
            if (text != null)
            {
                textLength = text.length();
            }
            else if (textLength < 0) { return false; }
            boolean filter = true;
            int balance = 0;
            for (int i = 0; i < textLength; i++)
            {
                char ch = text.charAt(i);
                switch (ch)
                {
                case ',':
                    filter = false;
                    break;
                case '(':
                    balance++;
                    filter = false;
                    break;
                case ')':
                    balance--;
                    filter = false;
                    break;
                }
                if (balance < 0) otherMethodContext = true;
            }
            if (otherMethodContext && balance < 0) otherMethodContext = false;
            if (queryMethodParamsStartPos == null
                    || caretOffset <= queryMethodParamsStartPos.getOffset())
                filter = false;
            return otherMethodContext || filter;
        }

        protected void filter(CompletionResultSet resultSet)
        {
            if (!otherMethodContext)
            {
                resultSet.setAnchorOffset(queryAnchorOffset);
                resultSet.setToolTip(queryToolTip);
            }
            resultSet.finish();
        }
    }
}
