/*
 * This file is part of the Scriba source distribution. This is free, open-source 
 * software. For full licensing information, please see the LicensingInformation file
 * at the root level of the distribution.
 *
 * Copyright (c) 2006-2007 Kobrix Software, Inc.
 */
package seco.storage;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGPlainLink;

public class DocLink extends HGPlainLink
{
	public DocLink(HGHandle[] outgoingSet) 
	{
		super(outgoingSet);
	}
	
	public int getArity()
	{
		return 2;
	}
}
