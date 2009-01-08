/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core.target.impl;

import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.provisional.frameworkadmin.BundleInfo;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.P2Utils;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.target.provisional.IBundleContainer;

/**
 * A bundle container representing an installed profile.
 * 
 * @since 3.5 
 */
class ProfileBundleContainer implements IBundleContainer {

	/**
	 * Path to home/root install location
	 */
	private String fHome;

	/**
	 * Alternate configuration location or <code>null</code> if default
	 */
	private String fConfiguration;

	/**
	 * Creates a new bundle container for the profile at the specified location.
	 * 
	 * @param home path in local file system
	 * @param configurationLocation alternate configuration location or <code>null</code> for default
	 */
	public ProfileBundleContainer(String home, String configurationLocation) {
		fHome = home;
		fConfiguration = configurationLocation;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.core.target.provisional.IBundleContainer#resolveBundles(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public BundleInfo[] resolveBundles(IProgressMonitor monitor) throws CoreException {
		URL configUrl = getConfigurationArea();
		BundleInfo[] infos = P2Utils.readBundles(new Path(fHome).toOSString(), configUrl);
		if (infos == null) {
			throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_0, fHome)));
		}
		return infos;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.core.target.provisional.IBundleContainer#resolveSourceBundles(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public BundleInfo[] resolveSourceBundles(IProgressMonitor monitor) throws CoreException {
		URL configUrl = getConfigurationArea();
		BundleInfo[] source = P2Utils.readSourceBundles(new Path(fHome).toOSString(), configUrl);
		if (source == null) {
			source = new BundleInfo[0];
		}
		return source;
	}

	/**
	 * Returns a URL to the configuration area associated with this profile.
	 * 
	 * @return configuration area URL
	 * @throws CoreException if unable to generate a URL
	 */
	private URL getConfigurationArea() throws CoreException {
		IPath home = new Path(fHome);
		IPath configuration = null;
		if (fConfiguration == null) {
			configuration = home.append("configuration"); //$NON-NLS-1$
		} else {
			configuration = new Path(fConfiguration);
		}
		try {
			return configuration.toFile().toURL();
		} catch (MalformedURLException e) {
			throw new CoreException(new Status(IStatus.ERROR, PDECore.PLUGIN_ID, NLS.bind(Messages.ProfileBundleContainer_1, fHome), e));
		}
	}

}
