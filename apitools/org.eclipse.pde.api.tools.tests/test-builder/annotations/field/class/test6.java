/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

import org.eclipse.pde.api.tools.annotations.NoExtend;

/**
 * Test unsupported @NoExtend annotation on fields in a class in the default package
 */
public class test6 {
	@NoExtend
	public Object f1 = null;
	@NoExtend
	protected int f2 = 0;
	@NoExtend
	private static char[] f3 = {};
	@NoExtend
	long f4 = 0L;
}
