package seco.langs.ruby;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JToolTip;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.internal.runtime.methods.DynamicMethod;

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
import seco.notebook.syntax.completion.JavaDocManager;
import seco.notebook.syntax.java.JavaPaintComponent;
import seco.notebook.syntax.java.JavaResultItem;
import seco.util.DocumentUtilities;


public class RubyCompletionProvider implements CompletionProvider
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
		if (queryType == COMPLETION_QUERY_TYPE)
			return new AsyncCompletionTask(new Query(component.getCaret()
					.getDot()), component);
		else if (queryType == DOCUMENTATION_QUERY_TYPE)
			return new AsyncCompletionTask(new DocQuery(null), component);
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
			RubyParser p = (RubyParser) sup.getParser();
			try
			{
				String s = sup.getCommandBeforePt(offset);
				// System.out.println("RubyCompProv - query: " + s + ":" +
				// offset);
				Object obj = p.resolveVar(s, offset);
				 if (obj != null)
				//System.out.println("RubyCompProv - query - obj: " + obj);
				
				if (obj == null)
				{
					resultSet.finish();
					return;
				}
				if (obj instanceof RubyClass)
				{
					populateRubyClass(resultSet, (RubyClass) obj);
					return;
				}else if (obj instanceof RubyModule)
				{
					populateRubyModule(resultSet, (RubyModule) obj);
					return;
				}
				Class<?> cls = obj.getClass();
				int mod = Modifier.PUBLIC;
				if (!p.evaled_or_guessed) 
				    cls = (Class<?>) obj;
				populateComplPopup(resultSet, cls, mod);
			}
			catch (Exception ex)
			{
				// stay silent on eval error
				// if (ex instanceof ScriptException)
				ex.printStackTrace();
			}
			resultSet.finish();
		}

		private void populateRubyClass(CompletionResultSet resultSet,
				RubyClass t)
		{
			// System.out.println("populateRubyClass: " + t);
		    resultSet.setTitle(t.toString());
			while (t != null)
			{
			    for (Object key : t.getMethods().keySet())
				{
					DynamicMethod m = (DynamicMethod) t.getMethods().get(key);
					if (m.getVisibility().isPrivate() || 
					        m.getVisibility().isProtected()) continue;
					JavaResultItem item = new RubyMethodResultItem(
							(String) key, "void");
					item.setSubstituteOffset(queryCaretOffset);
					resultSet.addItem(item);
				}
				t = t.getSuperClass();
			}
			resultSet.finish();
			queryResult = resultSet;
		}
		
		private void populateRubyModule(CompletionResultSet resultSet,
				RubyModule t)
		{
			resultSet.setTitle("RubyModule: " + t.getName());
			System.out.println("populateRubyModule: " + t);
			while (t != null)
			{
				for (Object key : t.getMethods().keySet())
				{
					//DynamicMethod m = (DynamicMethod) t.getMethods().get(key);
					//if (m.getVisibility().isPublic()) continue;
					JavaResultItem item = new RubyMethodResultItem(
							(String) key, "void");
					item.setSubstituteOffset(queryCaretOffset);
					resultSet.addItem(item);
				}
				t = t.getSuperClass();
			}
			resultSet.finish();
			
			queryResult = resultSet;
		}

		private void populateComplPopup(CompletionResultSet resultSet,
				Class<?> cls, int modifiers)
		{
			// System.out.println("BshCompProv - populateComplPopup: " + cls);
			resultSet.setTitle(cls.getCanonicalName());
			resultSet.setAnchorOffset(queryAnchorOffset);
			if (cls.isArray())
			{
				JavaResultItem item = new JavaResultItem.FieldResultItem(
						"length", Integer.TYPE, Modifier.PUBLIC);
				item.setSubstituteOffset(queryCaretOffset);
				resultSet.addItem(item);
			}
			for (Class<?> c : cls.getDeclaredClasses())
			{
				if (Modifier.isPrivate(c.getModifiers())) continue;
				// anonymous inner classes have empty simple name
				if (c.getSimpleName().length() == 0) continue;
				// System.out.println("BshCompl - inner classes: " + c + ":" +
				// c.getCanonicalName());
				JavaResultItem item = new JavaResultItem.ClassResultItem(c,
						false);
				item.setSubstituteOffset(queryCaretOffset);
				resultSet.addItem(item);
			}
			for (Field f : getFields(cls, modifiers))
			{
				// when we show the static and private fields some ugly inner
				// members arise too
				if (f.getName().indexOf('$') >= 0) continue;
				JavaResultItem item = new JavaResultItem.FieldResultItem(f, cls);
				item.setSubstituteOffset(queryCaretOffset);
				resultSet.addItem(item);
			}
			for (Method m : getMethods(cls, modifiers))
			{
				if (m.getName().indexOf('$') >= 0) continue;
				JavaResultItem item = new JavaResultItem.MethodItem(m);
				item.setSubstituteOffset(queryCaretOffset);
				resultSet.addItem(item);
			}
			queryResult = resultSet;
		}

		private static Collection<Method> getMethods(Class<?> cls, int comp_mod)
		{
			Set<Method> set = new HashSet<Method>();
			Method[] ms = cls.getDeclaredMethods();
			for (int i = 0; i < ms.length; i++)
				if (!filterMod(ms[i].getModifiers(), comp_mod)) set.add(ms[i]);
			ms = cls.getMethods();
			for (int i = 0; i < ms.length; i++)
				if (!filterMod(ms[i].getModifiers(), comp_mod)) set.add(ms[i]);
			return set;
		}

		private static Collection<Field> getFields(Class<?> cls, int comp_mod)
		{
			Set<Field> set = new HashSet<Field>();
			Field[] ms = cls.getDeclaredFields();
			for (int i = 0; i < ms.length; i++)
				if (!filterMod(ms[i].getModifiers(), comp_mod)) set.add(ms[i]);
			ms = cls.getFields();
			for (int i = 0; i < ms.length; i++)
				if (!filterMod(ms[i].getModifiers(), comp_mod)) set.add(ms[i]);
			return set;
		}

		// needed because there's no package-private modifier,
		// when comp_mod contains Modifier.PRIVATE, we allow
		// everything to pass, otherwise only public members
		private static boolean filterMod(int mod, int comp_mod)
		{
			boolean priv = (comp_mod & Modifier.PRIVATE) != 0;
			boolean stat = (comp_mod & Modifier.STATIC) != 0;
			if (stat && (mod & Modifier.STATIC) == 0) return true;
			if (!priv && (mod & Modifier.PUBLIC) == 0) return true;
			// if (!stat && (mod & Modifier.STATIC) != 0) return true;
			return false;
		}
	}

	public static class DocQuery extends AsyncCompletionQuery
	{
		private Object item;
		private JTextComponent component;
		private static Action goToSource = new AbstractAction() {
			public void actionPerformed(ActionEvent e)
			{
				if (e != null)
					Completion.get().hideDocumentation();
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
				resultSet.setDocumentation(new DocItem(item, null));
			resultSet.finish();
		}

		protected void prepareQuery(JTextComponent component)
		{
			this.component = component;
		}

	
		private class DocItem implements CompletionDocumentation
		{
			private String text;
			private JavaDoc javaDoc;
			private Object item;
			private URL url;

			public DocItem(Object item, JavaDoc javaDoc)
			{
				this.javaDoc = javaDoc != null ? javaDoc : new JavaDoc(
						component);
				this.javaDoc.docItem = this;
				this.javaDoc.setItem(item);
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

				private JavaDoc(JTextComponent component)
				{
				}

				private void setItem(Object item)
				{
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
			// Position oldPos = queryMethodParamsStartPos;
			queryMethodParamsStartPos = null;
			ScriptSupport sup = doc.getScriptSupport(caretOffset);
			if (sup == null || !(sup.getParser() instanceof RubyParser))
				return;
			RubyParser p = (RubyParser) sup.getParser();
			// TODO:
			if (p.getRootNode() == null)
			{
				resultSet.finish();
				return;
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
				if (caretOffset - queryCaretOffset > 0)
					text = DocumentUtilities.getText(doc, queryCaretOffset,
							caretOffset - queryCaretOffset);
				else if (caretOffset - queryCaretOffset < 0)
					text = DocumentUtilities.getText(doc, caretOffset,
							queryCaretOffset - caretOffset);
				else
					textLength = 0;
			}
			catch (BadLocationException e)
			{
			}
			if (text != null)
			{
				textLength = text.length();
			} else if (textLength < 0)
			{
				return false;
			}
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

	static class RubyMethodResultItem extends JavaResultItem.MethodItem
	{
		private static JavaPaintComponent.MethodPaintComponent mtdComponent = null;

		public RubyMethodResultItem(String mtdName, String type)
		{
			super(mtdName, type);
		}

		protected boolean isAddParams(){
			return false;
		}
		
		public Component getPaintComponent(boolean isSelected)
		{
			if (mtdComponent == null)
			{
				mtdComponent = new RubyMethodPaintComponent();
			}
			mtdComponent.setFeatureName(getName());
			mtdComponent.setModifiers(getModifiers());
			mtdComponent.setTypeName(getTypeName());
			mtdComponent.setTypeColor(getTypeColor());
			// mtdComponent.setParams(getParams());
			// mtdComponent.setExceptions(getExceptions());
			return mtdComponent;
		}
	}

	public static class RubyMethodPaintComponent extends
			JavaPaintComponent.MethodPaintComponent
	{
		 protected void drawParameterList(Graphics g, List prmList) {
	            
	     }
	}
}
