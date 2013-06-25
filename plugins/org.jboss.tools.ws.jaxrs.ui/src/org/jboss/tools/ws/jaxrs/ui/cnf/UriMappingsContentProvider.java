/******************************************************************************* 
 * Copyright (c) 2008 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Xavier Coulon - Initial API and implementation 
 ******************************************************************************/

package org.jboss.tools.ws.jaxrs.ui.cnf;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsEndpoint;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsEndpointChangedListener;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.IJaxrsMetamodel;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.JaxrsEndpointDelta;
import org.jboss.tools.ws.jaxrs.core.metamodel.domain.JaxrsMetamodelLocator;
import org.jboss.tools.ws.jaxrs.ui.internal.utils.Logger;

public class UriMappingsContentProvider implements ITreeContentProvider, IJaxrsEndpointChangedListener {

	private TreeViewer viewer;

	private Map<IProject, UriPathTemplateCategory> uriPathTemplateCategories = new HashMap<IProject, UriPathTemplateCategory>();

	@Override
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	@Override
	public Object[] getChildren(final Object parentElement) {
		if (parentElement instanceof IProject) {
			final IProject project = (IProject) parentElement;
			if (!uriPathTemplateCategories.containsKey(project)) {
				UriPathTemplateCategory uriPathTemplateCategory = new UriPathTemplateCategory(this, project);
				uriPathTemplateCategories.put(project, uriPathTemplateCategory);
			}
			Logger.debug("Displaying the UriPathTemplateCategory for project '{}'", project.getName());
			return new Object[] { uriPathTemplateCategories.get(project) };
		}
		if (parentElement instanceof UriPathTemplateCategory) {
			final UriPathTemplateCategory uriPathTemplateCategory = (UriPathTemplateCategory) parentElement;
			return uriPathTemplateCategory.getChildren();
		} else if (parentElement instanceof ITreeContentProvider) {
			Logger.debug("Displaying the children of '{}'", parentElement);
			return ((ITreeContentProvider) parentElement).getChildren(parentElement);
		}
		Logger.debug("*** No children for parent of type '{}' ***", parentElement.getClass().getName());
		return null;
	}

	@Override
	public Object getParent(Object element) {
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof IProject) {
			Logger.debug("Project '{}' has children: true", ((IProject)element).getName());
			return true;
		} else if (element instanceof ITreeContentProvider) {
			final boolean hasChildren = ((ITreeContentProvider) element).hasChildren(element);
			Logger.debug("Element {} has children: {}", element, hasChildren);
			return hasChildren;
		}
		Logger.debug("Element {} has not children", element);
		return false;
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TreeViewer) viewer;
	}

	@Override
	public void dispose() {
		if (uriPathTemplateCategories != null) {
			for (IProject project : uriPathTemplateCategories.keySet()) {
				try {
					final IJaxrsMetamodel metamodel = JaxrsMetamodelLocator.get(project);
					metamodel.removeListener(this);
				} catch (CoreException e) {
					Logger.error("Failed to remove listener on JAX-RS Metamodel", e);
				}
			}
		}

		uriPathTemplateCategories = null;

	}

	@Override
	public void notifyEndpointChanged(final JaxrsEndpointDelta delta) {
		switch (delta.getKind()) {
		case IJavaElementDelta.ADDED:
		case IJavaElementDelta.REMOVED:
			refreshContent(delta.getEndpoint().getProject());
			break;
		case IJavaElementDelta.CHANGED:
			refreshContent(delta.getEndpoint());
			break;
		}
	}

	/**
	 * Refresh the whole JAX-RS Content tree for the given Project
	 * 
	 * @param project
	 */
	protected void refreshContent(final IProject project) {
		if (uriPathTemplateCategories != null) {
			if (!uriPathTemplateCategories.containsKey(project)) {
				Logger.debug("Adding a UriPathTemplateCategory for project {} (case #1)", project.getName());
				UriPathTemplateCategory uriPathTemplateCategory = new UriPathTemplateCategory(this, project);
				uriPathTemplateCategories.put(project, uriPathTemplateCategory);
			}
			refreshContent(uriPathTemplateCategories.get(project));
		}
	}

	/**
	 * Refresh the JAX-RS Content tree for the given {@link IJaxrsEndpoint} only
	 * 
	 * @param project
	 */
	protected void refreshContent(final IJaxrsEndpoint endpoint) {
		// check if the viewer is already having the appropriate
		// UriPathTemplateCategory for the given project. If not,
		// it is a WaitWhileBuildingElement item, and the project itself must be
		// refresh to replace this temporary
		// element with the expected category.
		final IProject project = endpoint.getProject();
		if (!uriPathTemplateCategories.containsKey(project)) {
			refreshContent(project);
		}
		final UriPathTemplateCategory uriPathTemplateCategory = uriPathTemplateCategories.get(project);
		final Object target = uriPathTemplateCategory.getUriPathTemplateElement(endpoint);
		Logger.debug("Refreshing navigator view at level: {}", target.getClass().getName());
		// this piece of code must run in an async manner to avoid reentrant
		// call while viewer is busy.
		refreshContent(target);
	}

	/**
	 * Refresh the whole JAX-RS Content tree for the given target node and all
	 * its subelements
	 * 
	 * @param target
	 *            the node to refresh
	 */
	protected void refreshContent(final Object target) {
		// this piece of code must run in an async manner to avoid reentrant
		// call while viewer is busy.
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (viewer != null) {
					TreePath[] treePaths = viewer.getExpandedTreePaths();
					Logger.debug("*** Refreshing the viewer at target level: {} (viewer busy: {}) ***", target,
							viewer.isBusy());
					viewer.refresh(target, true);
					viewer.setExpandedTreePaths(treePaths);
					Logger.debug("*** Refreshing the viewer... done ***");
				} else {
					Logger.debug("*** Cannot refresh: viewer is null :-( ***");
				}
			}
		});
	}

	public static class LoadingStub {
		public LoadingStub() {
		}
	}

}
