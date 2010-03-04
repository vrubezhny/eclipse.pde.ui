/*******************************************************************************
 * Copyright (c) 2007, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource Corporation - ongoing enhancements
 *******************************************************************************/
package org.eclipse.pde.internal.launching.launcher;

import java.util.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.ifeature.*;
import org.eclipse.pde.internal.launching.IPDEConstants;
import org.eclipse.pde.launching.IPDELauncherConstants;

public class BundleLauncherHelper {

	public static final char VERSION_SEPARATOR = '*';

	public static Map getWorkspaceBundleMap(ILaunchConfiguration configuration) throws CoreException {
		return getWorkspaceBundleMap(configuration, null, IPDELauncherConstants.WORKSPACE_BUNDLES);
	}

	public static Map getTargetBundleMap(ILaunchConfiguration configuration) throws CoreException {
		return getTargetBundleMap(configuration, null, IPDELauncherConstants.TARGET_BUNDLES);
	}

	public static Map getMergedBundleMap(ILaunchConfiguration configuration, boolean osgi) throws CoreException {
		Set set = new HashSet();
		Map map = new HashMap();

		// if we are using the eclipse-based launcher, we need special checks
		if (!osgi) {

			checkBackwardCompatibility(configuration, true);

			if (configuration.getAttribute(IPDELauncherConstants.USE_DEFAULT, true)) {
				IPluginModelBase[] models = PluginRegistry.getActiveModels();
				for (int i = 0; i < models.length; i++) {
					addBundleToMap(map, models[i], "default:default"); //$NON-NLS-1$
				}
				return map;
			}

			if (configuration.getAttribute(IPDELauncherConstants.USEFEATURES, false)) {
				IPluginModelBase[] models = PluginRegistry.getWorkspaceModels();
				for (int i = 0; i < models.length; i++) {
					addBundleToMap(map, models[i], "default:default"); //$NON-NLS-1$
				}
				return map;
			}

			if (configuration.getAttribute(IPDELauncherConstants.USE_CUSTOM_FEATURES, false)) {
				// Get the default location settings
				String defaultLocation = configuration.getAttribute(IPDELauncherConstants.FEATURE_DEFAULT_LOCATION, IPDELauncherConstants.LOCATION_WORKSPACE);
				String defaultPluginResolution = configuration.getAttribute(IPDELauncherConstants.FEATURE_PLUGIN_RESOLUTION, IPDELauncherConstants.LOCATION_WORKSPACE);

				// Get all available features
				HashMap workspaceFeatureMap = new HashMap();
				HashMap externalFeatureMap = new HashMap();

				FeatureModelManager fmm = new FeatureModelManager();
				IFeatureModel[] workspaceFeatureModels = fmm.getWorkspaceModels();
				for (int i = 0; i < workspaceFeatureModels.length; i++) {
					String id = workspaceFeatureModels[i].getFeature().getId();
					workspaceFeatureMap.put(id, workspaceFeatureModels[i]);
				}
				fmm.shutdown();

				ExternalFeatureModelManager efmm = new ExternalFeatureModelManager();
				efmm.startup();
				IFeatureModel[] externalFeatureModels = efmm.getModels();
				for (int i = 0; i < externalFeatureModels.length; i++) {
					String id = externalFeatureModels[i].getFeature().getId();
					externalFeatureMap.put(id, externalFeatureModels[i]);
				}
				efmm.shutdown();

				// Get the selected features and their plugin resolution
				Map featureResolutionMap = new HashMap();
				Set selectedFeatures = configuration.getAttribute(IPDELauncherConstants.SELECTED_FEATURES, (Set) null);
				if (selectedFeatures != null) {
					for (Iterator iterator = selectedFeatures.iterator(); iterator.hasNext();) {
						String currentSelected = (String) iterator.next();
						String[] attributes = currentSelected.split(":"); //$NON-NLS-1$
						if (attributes.length > 1) {
							featureResolutionMap.put(attributes[0], attributes[1]);
						}
					}
				}

				// Get the feature model for each selected feature id and resolve its plugins
				Set launchPlugins = new HashSet();
				PluginModelManager pluginModelMgr = new PluginModelManager();
				for (Iterator iterator = featureResolutionMap.keySet().iterator(); iterator.hasNext();) {
					String id = (String) iterator.next();
					String location = (String) featureResolutionMap.get(id);

					IFeatureModel featureModel = null;
					if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(defaultLocation)) {
						featureModel = (IFeatureModel) workspaceFeatureMap.get(id);
					}
					if (featureModel == null || IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(defaultLocation)) {
						if (externalFeatureMap.containsKey(id)) {
							featureModel = (IFeatureModel) externalFeatureMap.get(id);
						}
					}
					if (featureModel == null) {
						continue;
					}

					IFeaturePlugin[] featurePlugins = featureModel.getFeature().getPlugins();
					String pluginResolution = (String) featureResolutionMap.get(id);
					if (IPDELauncherConstants.LOCATION_DEFAULT.equalsIgnoreCase(pluginResolution)) {
						pluginResolution = defaultPluginResolution;
					}

					for (int i = 0; i < featurePlugins.length; i++) {
						ModelEntry modelEntry = pluginModelMgr.findEntry(featurePlugins[i].getId());
						if (modelEntry == null) {
							continue;
						}
						IPluginModelBase model = null;
						if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(pluginResolution)) {
							model = getBestCandidateModel(modelEntry.getWorkspaceModels());
						}
						if (model == null || IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(pluginResolution)) {
							model = getBestCandidateModel(modelEntry.getExternalModels());
						}
						if (model == null || map.containsKey(model))
							continue;
						launchPlugins.add(model);
					}

					IFeatureImport[] featureImports = featureModel.getFeature().getImports();
					for (int i = 0; i < featureImports.length; i++) {
						if (featureImports[i].getType() == IFeatureImport.PLUGIN) {
							ModelEntry modelEntry = pluginModelMgr.findEntry(featureImports[i].getId());
							if (modelEntry == null) {
								continue;
							}
							IPluginModelBase model = null;
							if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(pluginResolution)) {
								model = getBestCandidateModel(modelEntry.getWorkspaceModels());
							} else if (IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(pluginResolution)) {
								model = getBestCandidateModel(modelEntry.getExternalModels());
							}

							if (model == null || map.containsKey(model))
								continue;
							launchPlugins.add(model);
						}
					}
				}

				// Get all required plugins
				// exclude "org.eclipse.ui.workbench.compatibility" - it is only needed for pre-3.0 bundles
				Set additionalIds = DependencyManager.getDependencies(launchPlugins.toArray(), false, new String[] {"org.eclipse.ui.workbench.compatibility"}); //$NON-NLS-1$
				Iterator it = additionalIds.iterator();
				while (it.hasNext()) {
					String id = (String) it.next();
					ModelEntry modelEntry = pluginModelMgr.findEntry(id);
					if (modelEntry == null) {
						continue;
					}
					IPluginModelBase model = null;
					if (IPDELauncherConstants.LOCATION_WORKSPACE.equalsIgnoreCase(defaultPluginResolution)) {
						model = getBestCandidateModel(modelEntry.getWorkspaceModels());
					}
					if (model == null || IPDELauncherConstants.LOCATION_EXTERNAL.equalsIgnoreCase(defaultPluginResolution)) {
						model = getBestCandidateModel(modelEntry.getExternalModels());
					}
					if (model == null || map.containsKey(model))
						continue;
					launchPlugins.add(model);
				}

				// Create the start levels for the selected plugins and add them to the map
				for (Iterator iterator = launchPlugins.iterator(); iterator.hasNext();) {
					addBundleToMap(map, (IPluginModelBase) iterator.next(), "default:default"); //$NON-NLS-1$
				}
				return map;
			}
		}

		String workspace = osgi == false ? IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS : IPDELauncherConstants.WORKSPACE_BUNDLES;
		String target = osgi == false ? IPDELauncherConstants.SELECTED_TARGET_PLUGINS : IPDELauncherConstants.TARGET_BUNDLES;
		map = getWorkspaceBundleMap(configuration, set, workspace);
		map.putAll(getTargetBundleMap(configuration, set, target));
		return map;
	}

	private static IPluginModelBase getBestCandidateModel(IPluginModelBase[] models) {
		IPluginModelBase model = null;
		for (int i = 0; i < models.length; i++) {
			if (models[i].getBundleDescription() == null)
				continue;

			if (model == null) {
				model = models[i];
				continue;
			}

			if (!model.isEnabled() && models[i].isEnabled()) {
				model = models[i];
				continue;
			}

			BundleDescription current = model.getBundleDescription();
			BundleDescription candidate = models[i].getBundleDescription();
			if (!current.isResolved() && candidate.isResolved()) {
				model = models[i];
				continue;
			}

			if (current.getVersion().compareTo(candidate.getVersion()) < 0) {
				model = models[i];
			}
		}
		return model;
	}

	public static IPluginModelBase[] getMergedBundles(ILaunchConfiguration configuration, boolean osgi) throws CoreException {
		Map map = getMergedBundleMap(configuration, osgi);
		return (IPluginModelBase[]) map.keySet().toArray(new IPluginModelBase[map.size()]);
	}

	public static Map getWorkspaceBundleMap(ILaunchConfiguration configuration, Set set, String attribute) throws CoreException {
		String selected = configuration.getAttribute(attribute, ""); //$NON-NLS-1$
		Map map = new HashMap();
		StringTokenizer tok = new StringTokenizer(selected, ","); //$NON-NLS-1$
		while (tok.hasMoreTokens()) {
			String token = tok.nextToken();
			int index = token.indexOf('@');
			if (index < 0) { // if no start levels, assume default
				token = token.concat("@default:default"); //$NON-NLS-1$
				index = token.indexOf('@');
			}
			String idVersion = token.substring(0, index);
			int versionIndex = idVersion.indexOf(VERSION_SEPARATOR);
			String id = (versionIndex > 0) ? idVersion.substring(0, versionIndex) : idVersion;
			String version = (versionIndex > 0) ? idVersion.substring(versionIndex + 1) : null;
			if (set != null)
				set.add(id);
			ModelEntry entry = PluginRegistry.findEntry(id);
			if (entry != null) {
				IPluginModelBase[] models = entry.getWorkspaceModels();
				for (int i = 0; i < models.length; i++) {
					IPluginBase base = models[i].getPluginBase();

					// match only if...
					// a) if we have the same version
					// b) no version
					// c) all else fails, if there's just one bundle available, use it
					if (base.getVersion().equals(version) || version == null || models.length == 1)
						addBundleToMap(map, models[i], token.substring(index + 1));
				}
			}
		}

		if (configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true)) {
			Set deselectedPlugins = LaunchPluginValidator.parsePlugins(configuration, IPDELauncherConstants.DESELECTED_WORKSPACE_PLUGINS);
			IPluginModelBase[] models = PluginRegistry.getWorkspaceModels();
			for (int i = 0; i < models.length; i++) {
				String id = models[i].getPluginBase().getId();
				if (id == null)
					continue;
				if (!deselectedPlugins.contains(models[i])) {
					if (set != null)
						set.add(id);
					if (!map.containsKey(models[i]))
						addBundleToMap(map, models[i], "default:default"); //$NON-NLS-1$
				}
			}
		}
		return map;
	}

	/**
	 * Adds the given bundle and start information to the map.  This will override anything set
	 * for system bundles, and set their start level to the appropriate level
	 * @param map The map to add the bundles too
	 * @param bundle The bundle to add
	 * @param substring the start information in the form level:autostart
	 */
	private static void addBundleToMap(Map map, IPluginModelBase bundle, String sl) {
		BundleDescription desc = bundle.getBundleDescription();
		boolean defaultsl = (sl == null || sl.equals("default:default")); //$NON-NLS-1$
		if (desc != null && defaultsl) {
			String modelName = desc.getSymbolicName();
			if (IPDEBuildConstants.BUNDLE_DS.equals(modelName)) {
				map.put(bundle, "1:true"); //$NON-NLS-1$ 
			} else if (IPDEBuildConstants.BUNDLE_SIMPLE_CONFIGURATOR.equals(modelName)) {
				map.put(bundle, "1:true"); //$NON-NLS-1$
			} else if (IPDEBuildConstants.BUNDLE_EQUINOX_COMMON.equals(modelName)) {
				map.put(bundle, "2:true"); //$NON-NLS-1$
			} else if (IPDEBuildConstants.BUNDLE_OSGI.equals(modelName)) {
				map.put(bundle, "-1:true"); //$NON-NLS-1$
			} else if (IPDEBuildConstants.BUNDLE_UPDATE_CONFIGURATOR.equals(modelName)) {
				map.put(bundle, "3:true"); //$NON-NLS-1$
			} else if (IPDEBuildConstants.BUNDLE_CORE_RUNTIME.equals(modelName)) {
				if (TargetPlatformHelper.getTargetVersion() > 3.1) {
					map.put(bundle, "default:true"); //$NON-NLS-1$
				} else {
					map.put(bundle, "2:true"); //$NON-NLS-1$
				}
			} else {
				map.put(bundle, sl);
			}
		} else {
			map.put(bundle, sl);
		}

	}

	public static Map getTargetBundleMap(ILaunchConfiguration configuration, Set set, String attribute) throws CoreException {
		String selected = configuration.getAttribute(attribute, ""); //$NON-NLS-1$
		Map map = new HashMap();
		StringTokenizer tok = new StringTokenizer(selected, ","); //$NON-NLS-1$
		while (tok.hasMoreTokens()) {
			String token = tok.nextToken();
			int index = token.indexOf('@');
			if (index < 0) { // if no start levels, assume default
				token = token.concat("@default:default"); //$NON-NLS-1$
				index = token.indexOf('@');
			}
			String idVersion = token.substring(0, index);
			int versionIndex = idVersion.indexOf(VERSION_SEPARATOR);
			String id = (versionIndex > 0) ? idVersion.substring(0, versionIndex) : idVersion;
			String version = (versionIndex > 0) ? idVersion.substring(versionIndex + 1) : null;
			if (set != null && set.contains(id))
				continue;
			ModelEntry entry = PluginRegistry.findEntry(id);
			if (entry != null) {
				IPluginModelBase[] models = entry.getExternalModels();
				for (int i = 0; i < models.length; i++) {
					if (models[i].isEnabled()) {
						IPluginBase base = models[i].getPluginBase();
						// match only if...
						// a) if we have the same version
						// b) no version
						// c) all else fails, if there's just one bundle available, use it
						if (base.getVersion().equals(version) || version == null || models.length == 1)
							addBundleToMap(map, models[i], token.substring(index + 1));
					}
				}
			}
		}
		return map;
	}

	public static String writeBundleEntry(IPluginModelBase model, String startLevel, String autoStart) {
		IPluginBase base = model.getPluginBase();
		String id = base.getId();
		StringBuffer buffer = new StringBuffer(id);

		ModelEntry entry = PluginRegistry.findEntry(id);
		if (entry != null && entry.getActiveModels().length > 1) {
			buffer.append(VERSION_SEPARATOR);
			buffer.append(model.getPluginBase().getVersion());
		}

		boolean hasStartLevel = (startLevel != null && startLevel.length() > 0);
		boolean hasAutoStart = (autoStart != null && autoStart.length() > 0);

		if (hasStartLevel || hasAutoStart)
			buffer.append('@');
		if (hasStartLevel)
			buffer.append(startLevel);
		if (hasStartLevel || hasAutoStart)
			buffer.append(':');
		if (hasAutoStart)
			buffer.append(autoStart);
		return buffer.toString();
	}

	public static void checkBackwardCompatibility(ILaunchConfiguration configuration, boolean save) throws CoreException {
		ILaunchConfigurationWorkingCopy wc = null;
		if (configuration.isWorkingCopy()) {
			wc = (ILaunchConfigurationWorkingCopy) configuration;
		} else {
			wc = configuration.getWorkingCopy();
		}

		String value = configuration.getAttribute("wsproject", (String) null); //$NON-NLS-1$
		if (value != null) {
			wc.setAttribute("wsproject", (String) null); //$NON-NLS-1$
			if (value.indexOf(';') != -1) {
				value = value.replace(';', ',');
			} else if (value.indexOf(':') != -1) {
				value = value.replace(':', ',');
			}
			value = (value.length() == 0 || value.equals(",")) //$NON-NLS-1$
			? null
					: value.substring(0, value.length() - 1);

			boolean automatic = configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true);
			String attr = automatic ? IPDELauncherConstants.DESELECTED_WORKSPACE_PLUGINS : IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS;
			wc.setAttribute(attr, value);
		}

		String value2 = configuration.getAttribute("extplugins", (String) null); //$NON-NLS-1$
		if (value2 != null) {
			wc.setAttribute("extplugins", (String) null); //$NON-NLS-1$
			if (value2.indexOf(';') != -1) {
				value2 = value2.replace(';', ',');
			} else if (value2.indexOf(':') != -1) {
				value2 = value2.replace(':', ',');
			}
			value2 = (value2.length() == 0 || value2.equals(",")) ? null : value2.substring(0, value2.length() - 1); //$NON-NLS-1$
			wc.setAttribute(IPDELauncherConstants.SELECTED_TARGET_PLUGINS, value2);
		}

		String version = configuration.getAttribute(IPDEConstants.LAUNCHER_PDE_VERSION, (String) null);
		boolean newApp = TargetPlatformHelper.usesNewApplicationModel();
		boolean upgrade = !"3.3".equals(version) && newApp; //$NON-NLS-1$
		if (!upgrade)
			upgrade = TargetPlatformHelper.getTargetVersion() >= 3.2 && version == null;
		if (upgrade) {
			wc.setAttribute(IPDEConstants.LAUNCHER_PDE_VERSION, newApp ? "3.3" : "3.2a"); //$NON-NLS-1$ //$NON-NLS-2$
			boolean usedefault = configuration.getAttribute(IPDELauncherConstants.USE_DEFAULT, true);
			boolean useFeatures = configuration.getAttribute(IPDELauncherConstants.USEFEATURES, false);
			boolean automaticAdd = configuration.getAttribute(IPDELauncherConstants.AUTOMATIC_ADD, true);
			if (!usedefault && !useFeatures) {
				ArrayList list = new ArrayList();
				if (version == null) {
					list.add("org.eclipse.core.contenttype"); //$NON-NLS-1$
					list.add("org.eclipse.core.jobs"); //$NON-NLS-1$
					list.add(IPDEBuildConstants.BUNDLE_EQUINOX_COMMON);
					list.add("org.eclipse.equinox.preferences"); //$NON-NLS-1$
					list.add("org.eclipse.equinox.registry"); //$NON-NLS-1$
					list.add("org.eclipse.core.runtime.compatibility.registry"); //$NON-NLS-1$
				}
				if (!"3.3".equals(version) && newApp) //$NON-NLS-1$
					list.add("org.eclipse.equinox.app"); //$NON-NLS-1$
				StringBuffer extensions = new StringBuffer(configuration.getAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS, "")); //$NON-NLS-1$
				StringBuffer target = new StringBuffer(configuration.getAttribute(IPDELauncherConstants.SELECTED_TARGET_PLUGINS, "")); //$NON-NLS-1$
				for (int i = 0; i < list.size(); i++) {
					String plugin = list.get(i).toString();
					IPluginModelBase model = PluginRegistry.findModel(plugin);
					if (model == null)
						continue;
					if (model.getUnderlyingResource() != null) {
						if (automaticAdd)
							continue;
						if (extensions.length() > 0)
							extensions.append(","); //$NON-NLS-1$
						extensions.append(plugin);
					} else {
						if (target.length() > 0)
							target.append(","); //$NON-NLS-1$
						target.append(plugin);
					}
				}
				if (extensions.length() > 0)
					wc.setAttribute(IPDELauncherConstants.SELECTED_WORKSPACE_PLUGINS, extensions.toString());
				if (target.length() > 0)
					wc.setAttribute(IPDELauncherConstants.SELECTED_TARGET_PLUGINS, target.toString());
			}
		}

		if (save && (value != null || value2 != null || upgrade))
			wc.doSave();
	}

}
