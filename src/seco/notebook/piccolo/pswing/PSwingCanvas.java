/* Copyright 2003-2005, University of Colorado */

/*
 * CVS Info -
 * Filename : $Source: /home/buildspace/CodeBank/Projects/CCK/piccolo/extras/edu/umd/cs/piccolox/pswing/PSwingCanvas.java,v $
 * Branch : $Name:  $
 * Modified by : $Author: eburke $
 * Revision : $Revision: 1.1 $
 * Date modified : $Date: 2006/11/08 18:57:56 $
 */
package seco.notebook.piccolo.pswing;

import edu.umd.cs.piccolo.PCanvas;

import javax.swing.*;
import java.awt.*;

/**
 * The <b>PSwingCanvas</b> is a PCanvas that can display Swing components with the PSwing adapter.
 *
 * @author Benjamin B. Bederson
 * @author Sam R. Reid
 * @author Lance E. Good
 */

public class PSwingCanvas extends PCanvas {
    public static final String SWING_WRAPPER_KEY = "Swing Wrapper";
    private static PSwingRepaintManager pSwingRepaintManager = new PSwingRepaintManager();

    private SwingWrapper swingWrapper;
    private PSwingEventHandler swingEventHandler;

    private void init()
    {
        swingWrapper = new SwingWrapper( this );
        add( swingWrapper );
        RepaintManager.setCurrentManager( pSwingRepaintManager );
        pSwingRepaintManager.addPSwingCanvas( this );

        swingEventHandler = new PSwingEventHandler( this, getCamera() );//todo or maybe getCameraLayer() or getRoot()?
        swingEventHandler.setActive( true );               
    }
    
    /**
     * Construct a new PSwingCanvas.
     */
    public PSwingCanvas() 
    {
        init();
    }
    
//    public PSwingCanvas(PNode parent)
//    {
//        super(parent);
//        init();
//    }
    
    JComponent getSwingWrapper() {
        return swingWrapper;
    }

    static class SwingWrapper extends JComponent {
        private PSwingCanvas pSwingCanvas;

        public SwingWrapper( PSwingCanvas pSwingCanvas ) {
            this.pSwingCanvas = pSwingCanvas;
            setSize( new Dimension( 0, 0 ) );
            setPreferredSize( new Dimension( 0, 0 ) );
            putClientProperty( SWING_WRAPPER_KEY, SWING_WRAPPER_KEY );
        }

        public PSwingCanvas getpSwingCanvas() {
            return pSwingCanvas;
        }
    }

}