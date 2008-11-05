/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.search;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.IApiAnnotations;
import org.eclipse.pde.api.tools.internal.provisional.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.IApiDescription;
import org.eclipse.pde.api.tools.internal.provisional.RestrictionModifiers;
import org.eclipse.pde.api.tools.internal.provisional.VisibilityModifiers;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IElementDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiMember;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiType;
import org.eclipse.pde.api.tools.internal.provisional.model.IReference;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblem;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblemTypes;
import org.eclipse.pde.api.tools.internal.util.Util;

/**
 * Detects leaks in method return types and parameters
 * 
 * @since 1.1
 * @noextend This class is not intended to be subclassed by clients.
 */
public abstract class MethodLeakDetector extends AbstractLeakProblemDetector {

	/**
	 * @param nonApiPackageNames
	 */
	public MethodLeakDetector(Set nonApiPackageNames) {
		super(nonApiPackageNames);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getElementType(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected int getElementType(IReference reference) {
		return IElementDescriptor.METHOD;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getProblemKind()
	 */
	protected int getProblemKind() {
		return IApiProblem.API_LEAK;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getSeverityKey()
	 */
	protected String getSeverityKey() {
		return IApiProblemTypes.LEAK_METHOD_RETURN_TYPE ;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#isProblem(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected boolean isProblem(IReference reference) {
		IApiMethod method = (IApiMethod) reference.getMember();
		IApiType type = (IApiType) reference.getResolvedReference();
		try {
			// referenced type is non-API
			IApiAnnotations annotations = type.getApiComponent().getApiDescription().resolveAnnotations(type.getHandle());
			if (annotations != null) {
				if (VisibilityModifiers.isPrivate(annotations.getVisibility())) {
					if ((Flags.AccProtected & method.getModifiers()) > 0) {
						// ignore protected members if contained in a @noextend type
						// TODO: we could perform this check before resolution - it's on the source location
						IApiDescription description = method.getApiComponent().getApiDescription();
						annotations = description.resolveAnnotations(method.getHandle().getEnclosingType());
						if (annotations == null || RestrictionModifiers.isExtendRestriction(annotations.getRestrictions())) {
							// ignore
							return false;
						}
					}
					return true;
				}
			}
		} catch (CoreException e) {
			ApiPlugin.log(e);
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getMessageArgs(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected String[] getMessageArgs(IReference reference) throws CoreException {
		IApiMethod method = (IApiMethod) reference.getMember();
		IApiType type = (IApiType) reference.getResolvedReference();
		String methodName = method.getName();
		if (method.isConstructor()) {
			methodName = getSimpleTypeName(method);
		}
		return new String[] {getSimpleTypeName(type), getSimpleTypeName(method), Signature.toString(method.getSignature(), methodName, null, false, false)};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getQualifiedMessageArgs(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected String[] getQualifiedMessageArgs(IReference reference) throws CoreException {
		IApiMethod method = (IApiMethod) reference.getMember();
		IApiType type = (IApiType) reference.getResolvedReference();
		String methodName = method.getName();
		if (method.isConstructor()) {
			methodName = getSimpleTypeName(method);
		}
		return new String[] {getTypeName(type), getTypeName(method), Signature.toString(method.getSignature(), methodName, null, false, false)};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getSourceRange(org.eclipse.jdt.core.IType, org.eclipse.jface.text.IDocument, org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected Position getSourceRange(IType type, IDocument doc, IReference reference) throws CoreException, BadLocationException {
		// report the marker on the method
		IApiMethod method = (IApiMethod) reference.getMember();
		String[] parameterTypes = Signature.getParameterTypes(method.getSignature());
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypes[i] = parameterTypes[i].replace('/', '.');
		}
		String methodname = method.getName();
		if(method.isConstructor()) {
			IApiType enclosingType = method.getEnclosingType();
			if (enclosingType.isMemberType() && !Flags.isStatic(enclosingType.getModifiers())) {
				// remove the synthetic argument that corresponds to the enclosing type
				int length = parameterTypes.length - 1;
				System.arraycopy(parameterTypes, 1, (parameterTypes = new String[length]), 0, length);
			}
			methodname = enclosingType.getSimpleName();
		}
		IMethod Qmethod = type.getMethod(methodname, parameterTypes);
		IMethod[] methods = type.getMethods();
		IMethod match = null;
		for (int i = 0; i < methods.length; i++) {
			IMethod m = methods[i];
			if (m.isSimilar(Qmethod)) {
				match = m;
				break;
			}
		}
		Position pos = null;
		if (match != null) {
			ISourceRange range = match.getNameRange();
			if(range != null) {
				pos = new Position(range.getOffset(), range.getLength());
			}
		}
		if(pos == null) {
			noSourcePosition(type, reference);
		}
		return pos;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.search.IApiProblemDetector#considerReference(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	public boolean considerReference(IReference reference) {
		if (isNonAPIReference(reference)) {
			IApiMember member = reference.getMember();
			if (matchesSourceModifiers(member) && matchesSourceApiRestrictions(member)) {
				retainReference(reference);
				return true;
			}
		}
		return false;
	}
	
	protected boolean matchesSourceApiRestrictions(IApiMember member) {
		IApiComponent apiComponent = member.getApiComponent();
		try {
			IApiMethod method = (IApiMethod) member;
			IApiAnnotations annotations = apiComponent.getApiDescription().resolveAnnotations(method.getHandle());
			if (annotations != null) {
				if (VisibilityModifiers.isAPI(annotations.getVisibility())) {
					int ares = annotations.getRestrictions();
					if(ares != 0) {
						if(method.isConstructor()) {
							return (ares & RestrictionModifiers.NO_REFERENCE) == 0;
						}
						if((ares & RestrictionModifiers.NO_OVERRIDE) == 0) {
							IApiAnnotations annot = apiComponent.getApiDescription().resolveAnnotations(method.getEnclosingType().getHandle());
							int pres = 0;
							if(annot != null) {
								pres = annot.getRestrictions();
							}
							return (ares & RestrictionModifiers.NO_REFERENCE) != 0 && (!Util.isFinal(method.getModifiers())
									&& !Util.isStatic(method.getModifiers())
									&& !Util.isFinal(method.getEnclosingType().getModifiers())
									&& ((pres & RestrictionModifiers.NO_EXTEND) == 0));
						}
						return  (ares & RestrictionModifiers.NO_REFERENCE) == 0; 
					}
					else {
						return true;
						//return fSourceRestriction != 0;
					}
				}
			} else {
				return true;
			}
		} catch (CoreException e) {
			ApiPlugin.log(e);
		}
		return false;
	}	
	
	protected boolean matchesSourceModifiers(IApiMember member) {
		while (member != null) {
			int modifiers = member.getModifiers();
			if (Util.isPublic(modifiers) || Util.isProtected(modifiers)) {
				try {
					member = member.getEnclosingType();
				} catch (CoreException e) {
					ApiPlugin.log(e.getStatus());
					return false;
				}
			} else {
				return false;
			}
		}
		return true;
	}	

}
