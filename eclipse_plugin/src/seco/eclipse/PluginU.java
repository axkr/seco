package seco.eclipse;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.albireo.core.AwtEnvironment;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import seco.boot.NicheManager;
import seco.boot.NicheSelectDialog;

public class PluginU
{
    private static SecoView view;

    public static SecoView getSecoView()
    {
        if (view != null) return view;

        IWorkbench win = PlatformUI.getWorkbench();
        if (win.getActiveWorkbenchWindow() == null) return null;
        IWorkbenchPage activePage = win.getActiveWorkbenchWindow()
                .getActivePage();
        IViewPart part =  (activePage != null) ? activePage
                .findView(SecoView.ID) : null;
        return part instanceof SecoView ? view = (SecoView) part : null;        
    }

    public static void showMessage(String message)
    {
        if (getSecoView() != null)
            MessageDialog.openInformation(getSecoView().getSite().getShell(),
                    "Seco", message);
    }

    public static void openNichesDlg(IWorkbenchWindow window)
    {
        AwtEnvironment env = AwtEnvironment.getInstance(window.getShell()
                .getDisplay());
        final Map<String, File> niches = NicheManager.readNiches();
        RunnableWithResult run = new RunnableWithResult() {
            NicheSelectDialog dlg;

            public void run()
            {
                dlg = new NicheSelectDialog();
                dlg.setNiches(niches);
                dlg.setVisible(true);
            }

            public Object getResult()
            {
                return dlg.getSucceeded() ? niches.get(dlg.getSelectedNiche())
                        .getAbsolutePath() : null;
            }

        };
        env.invokeAndBlockSwt(run);
        if (run.getResult() != null)
        {
            String nicheLocation = (String) run.getResult();
            try
            {
                SecoPlugin.getDefault().setNicheLocation(nicheLocation);
            }
            catch (Throwable t)
            {

            }
        }
    }

    public static void openSecoView()
    {
        IWorkbench win = PlatformUI.getWorkbench();
        IWorkbenchPage activePage = win.getActiveWorkbenchWindow()
                .getActivePage();
        try
        {
            if (activePage != null)
                view = (SecoView) activePage.showView(SecoView.ID);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }

    interface RunnableWithResult extends Runnable
    {
        Object getResult();
    }

    public static List<TypeNameMatch> findClass(String name)
    {
        final ArrayList<TypeNameMatch> list = new ArrayList<TypeNameMatch>();
        TypeNameMatchRequestor requestor = new TypeNameMatchRequestor() {
            public void acceptTypeNameMatch(TypeNameMatch match)
            {
                list.add(match);
            }
        };
        SearchEngine engine = new SearchEngine((WorkingCopyOwner) null);
        int flags = SearchPattern.R_EXACT_MATCH;
        // | SearchPattern.R_PREFIX_MATCH
        // | SearchPattern.R_PATTERN_MATCH
        // | SearchPattern.R_CAMELCASE_MATCH
        // | SearchPattern.R_CAMELCASE_SAME_PART_COUNT_MATCH;
        String pck = name.indexOf(".") > 0 ? name.substring(0, name
                .lastIndexOf('.')) : null;
        if (pck != null) name = name.substring(name.lastIndexOf('.') + 1);
        try
        {
            engine.searchAllTypeNames(pck != null ? pck.toCharArray() : null,
                    SearchPattern.R_EXACT_MATCH, name.toCharArray(), flags,
                    IJavaSearchConstants.TYPE, SearchEngine
                            .createWorkspaceScope(), requestor,
                    IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return list;
    }

    public static void searchAndOpen(String name)
    {
        List<TypeNameMatch> list = findClass(name);
        if (!list.isEmpty())
        {
            // trying to find .java file to open
            for (TypeNameMatch m : list)
                if (m.getType().getClass().getName().indexOf("SourceType") > 0)
                {
                    openInEditor(m.getType());
                    return;
                }
            // just open the first .class file
            openInEditor(list.get(0).getType());
        }
    }

    public static void openInEditor(final IJavaElement el)
    {
        runInEclipseGUIThread(new Runnable() {
            public void run()
            {
                try
                {
                    JavaUI.openInEditor(el, true, true);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        });
    }

    public static void runInEclipseGUIThread(Runnable r)
    {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(r);
    }
    
    public static void setStatusLineMsg(final String message)
    {
        final Display display = Display.getDefault();

        new Thread() {

            public void run()
            {

                display.syncExec(new Runnable() {

                    public void run()
                    {

                        IWorkbench wb = PlatformUI.getWorkbench();
                        IWorkbenchWindow win = wb.getActiveWorkbenchWindow();

                        IWorkbenchPage page = win.getActivePage();

                        IWorkbenchPart part = page.getActivePart();
                        IWorkbenchPartSite site = part.getSite();

                        IViewSite vSite = (IViewSite) site;

                        IActionBars actionBars = vSite.getActionBars();

                        if (actionBars == null) return;

                        IStatusLineManager statusLineManager = actionBars
                                .getStatusLineManager();

                        if (statusLineManager == null) return;

                        statusLineManager.setMessage(message);
                    }
                });
            }
        }.start();
    }
    
    /**
     * makes a window request a users attention
     * @param tempMessage a message for the user to know why attention is needed
     */
    static void requestUserAttention (String tempMessage)
    {
        //rate at which the title will change in milliseconds
        int rateOfChange = 1000;
        
        final Shell window = getSecoView().getSite().getShell();
        //flash n times and thats it
        int n = 5;
        final String orgText = window.getText();
        final String message = tempMessage;
        
        window.setData("requestUserAttention", true);
        window.addShellListener(new ShellAdapter (){
            @Override
            public void shellActivated (ShellEvent e)
            {
                window.setData("requestUserAttention", false);
            }
        });
        for (int x=0;x<n;x++)
        {
            window.getDisplay().timerExec(2*rateOfChange*x-rateOfChange, new Runnable (){
                @Override
                public void run ()
                {
                    if (((Boolean)window.getData("requestUserAttention")))
                    {
                        window.setText(message);
                    }
                }});
            window.getDisplay().timerExec(2*rateOfChange*x, new Runnable (){
                @Override
                public void run ()
                {
                    if (((Boolean)window.getData("requestUserAttention")) || window.getText().equals(message))
                    {
                        window.setText(orgText);
                    }
                }});
        }
    }


    public static IType getClassIType(Class<?> cls)
    {
        // final ArrayList<SearchMatch> list = new ArrayList<SearchMatch>();
        // SearchRequestor requestor = new SearchRequestor() {
        // public void acceptSearchMatch(SearchMatch match)
        // {
        // list.add(match);
        // }
        // };
        // SearchPattern pat = SearchPattern.createPattern(cls.getName(),
        // IJavaSearchConstants.CLASS_AND_INTERFACE,
        // IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
        // SearchEngine engine = new SearchEngine((WorkingCopyOwner) null);
        // try{
        // engine.search(pat, new SearchParticipant[] {
        // SearchEngine.getDefaultSearchParticipant()},
        // SearchEngine.createWorkspaceScope(), requestor, null);
        // }catch(Exception ex)
        // {
        //            
        // }
        // return (IJavaElement) list.get(0).getElement();
        List<TypeNameMatch> list = findClass(cls.getName());
        if (list.isEmpty()) return null;
        // trying to find .java file to open
        for (TypeNameMatch m : list)
            if (m.getType().getClass().getName().indexOf("SourceType") > 0) { return m
                    .getType(); }
        return list.get(0).getType();
    }

    public static IMethod getIMethod(Method m)
    {
        IType cl = getClassIType(m.getDeclaringClass());
        if (cl == null) return null;
        String[] params = new String[m.getParameterTypes().length];
        for (int i = 0; i < params.length; i++)
        {
            params[i] = PluginU.getClassIType(m.getParameterTypes()[i])
                    .getElementName();
        }

        return cl.getMethod(m.getName(), params);
    }
    
    // static File _pluginFolder;
    //
    // public static File getPluginFolder() {
    // if(_pluginFolder == null) {
    // URL url = Platform.getBundle(
    // SecoPlugin.PLUGIN_ID).getEntry("/");
    // try {
    // url = Platform.resolve(url);
    // }
    // catch(IOException ex) {
    // ex.printStackTrace();
    // }
    // _pluginFolder = new File(url.getPath());
    // }
    //
    // return _pluginFolder;
    // }
}
