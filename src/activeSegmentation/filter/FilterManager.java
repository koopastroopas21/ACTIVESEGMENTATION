package activeSegmentation.filter;


import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import activeSegmentation.ASCommon;
import activeSegmentation.FilterType;
import activeSegmentation.IProjectManager;
import activeSegmentation.LearningType;
import activeSegmentation.IFilter;
import activeSegmentation.IFilterManager;
import activeSegmentation.ProjectType;
import activeSegmentation.feature.FeatureContainer;
import activeSegmentation.feature.FeatureManager;
import activeSegmentation.prj.ProjectInfo;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ijaux.scale.Pair;

/**
 * 				
 *   
 * 
 * @author Sumit Kumar Vohra and Dimiter Prodanov , IMEC
 *
 *
 * @contents
 * Filter manager is responsible of loading  new filter from jar, 
 * change the setting of filter, generate the filter results
 * 
 * 
 * @license This library is free software; you can redistribute it and/or
 *      modify it under the terms of the GNU Lesser General Public
 *      License as published by the Free Software Foundation; either
 *      version 2.1 of the License, or (at your option) any later version.
 *
 *      This library is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *       Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public
 *      License along with this library; if not, write to the Free Software
 *      Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
public class FilterManager extends URLClassLoader implements IFilterManager {

	private Map<String,IFilter> filterMap= new HashMap<String, IFilter>();
	//private Map<Integer,FeatureType> featurStackMap= new HashMap<Integer, FeatureType>();
	private IProjectManager projectManager;
	private ProjectInfo projectInfo;

	private ProjectType projectType;

	private FeatureManager  featureManager;

	public FilterManager(IProjectManager projectManager, FeatureManager  featureManager){
		super(new URL[0], IJ.class.getClassLoader());
		
		this.projectManager= projectManager;
		this.projectInfo=projectManager.getMetaInfo();
		projectType=ProjectType.valueOf(this.projectInfo.getProjectType());
		IJ.log("Project Type: " + ProjectType.valueOf(this.projectInfo.getProjectType()));
		IJ.log("Loading Filters");
	 
		try {
			List<String> jars=projectInfo.getPluginPath();
			System.out.println(jars);
			if (jars!=null)
				loadFilters(jars);
			IJ.log("Filters Loaded");
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException | IOException e) {
			e.printStackTrace();
			IJ.log("Filters NOT Loaded. Check path");
		}
		
		this.featureManager= featureManager;
	}


	public  void loadFilters(List<String> plugins) throws InstantiationException, IllegalAccessException, 
	IOException, ClassNotFoundException {

		//System.out.println("home: "+home);
		//File f=new File(home);
		//String[] plugins = f.list();
		List<String> classes=new ArrayList<String>();
		String cp=System.getProperty("java.class.path");
		
		for(String plugin: plugins){
		
			if(plugin.endsWith(ASCommon.JAR))	{ 
				classes.addAll(installJarPlugins(plugin));
				
				cp+=";" + plugin;
				System.setProperty("java.class.path", cp);
				
				File g = new File(plugin);
				if (g.isFile())
					addJar(g);
			}
			System.out.println("classpath:  "+cp);

		}
		System.setProperty("java.class.path", cp);
		ClassLoader classLoader= FilterManager.class.getClassLoader();
		for(String plugin: classes){
			//System.out.println(plugin);
			Class<?>[] classesList=(classLoader.loadClass(plugin)).getInterfaces();
			for(Class<?> cs:classesList){
				if(cs.getSimpleName().equals(ASCommon.IFILTER)){
					//System.out.println(cs.getSimpleName());
					//IJ.log(plugin);
					//IJ.debugMode=true;
					IFilter	thePlugIn =(IFilter) (classLoader.loadClass(plugin)).newInstance(); 
					if (projectType==ProjectType.SEGM && thePlugIn.getFilterType()==FilterType.SEGM ) {
				//	if (thePlugIn.getFilterType()==projectType.getProjectType()){
						System.out.println(thePlugIn.getKey());
						//TODO read annotations if present
						// populate the second map
						filterMap.put(thePlugIn.getKey(), thePlugIn);
					}

				}
			}

		}

		setFiltersMetaData();

	}

	private void addJar(File f) throws IOException {
		if (f.getName().endsWith(".jar")) {
			try {
				addURL(f.toURI().toURL());
			} catch (MalformedURLException e) {
				System.out.println("PluginClassLoader: "+e);
			}
		}
	}
	
	private List<String> loadImages(String directory){
		List<String> imageList= new ArrayList<String>();
		File folder = new File(directory);
		File[] images = folder.listFiles();
		for (File file : images) {
			if (file.isFile()) {
				imageList.add(file.getName());
			}
		}
		return imageList;
	}
	
	public void applyFilters(){
		String projectString=this.projectInfo.getProjectDirectory().get(ASCommon.IMAGESDIR);
		String filterString=this.projectInfo.getProjectDirectory().get(ASCommon.FILTERSDIR);
 
		Map<String,List<Pair<String,double[]>>> featureList= new HashMap<>();
		List<String>images= loadImages(projectString);
        Map<String,Set<String>> features= new HashMap<String,Set<String>>();
		
        for(IFilter filter: filterMap.values()){
			//System.out.println("filter applied"+filter.getName());
			if(filter.isEnabled()){
				// classification case
				// if(filter.getFilterType()==ProjectType.CLASSIF.getProjectType()){
				if (filter.getFilterType()==FilterType.CLASSIF
						&& projectType==ProjectType.CLASSIF ){
					for(String image: images) {
						for(String key: featureManager.getClassKeys()) {
							List<Roi> rois=featureManager.getExamples(key, LearningType.TRAINING_TESTING.name(), image);
							if(rois!=null && !rois.isEmpty()) {
								filter.applyFilter(new ImagePlus(projectString+image).getProcessor(),
										filterString+image.substring(0, image.lastIndexOf(".")),
										rois);
								if(filter.getFeatures()!=null) {
									features.put(filter.getKey(),filter.getFeatureNames());
									List<Pair<String,double[]>> featureL=filter.getFeatures();
									featureList.put(filter.getKey(),featureL);
								}

							}

						}
					}
					
				} else {
					for(String image: images) {
						//IJ.log(image);
						filter.applyFilter(new ImagePlus(projectString+image).getProcessor(),filterString+image.substring(0, image.lastIndexOf(".")), null);
					}

				}

			}

		}
		if(featureList!=null && featureList.size()>0) {
	 
			IJ.log("Features computed "+featureList.size());
			projectInfo.setFeatures(featureList);
			projectInfo.setFeatureNames(features);
 
		}

	}


	public Set<String> getAllFilters(){
		return filterMap.keySet();
	}
	
	
	public IFilter getFilter(String key){
		return filterMap.get(key);
	}

	public Map<String,String> getDefaultFilterSettings(String key){
		return filterMap.get(key).getDefaultSettings();
	}


	public boolean isFilterEnabled(String key){

		return filterMap.get(key).isEnabled();
	}


	public boolean updateFilterSettings(String key, Map<String,String> settingsMap){

		return filterMap.get(key).updateSettings(settingsMap);
	}

	/*
	public int getNumOfFeatures(String featureName) {
		return 0;
	}
*/



	private  List<String> installJarPlugins(String plugin) throws IOException {
		List<String> classNames = new ArrayList<String>();
		ZipInputStream zip = new ZipInputStream(new FileInputStream(plugin));
		for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
			if (!entry.isDirectory() && entry.getName().endsWith(ASCommon.DOTCLASS)) {
				String className = entry.getName().replace('/', '.'); // including ".class"
				classNames.add(className.substring(0, className.length() - ASCommon.DOTCLASS.length()));
			}
		}
		zip.close();
		return classNames;
	}



	/*	public Instance createInstance(String featureName, int x, int y, int classIndex, int sliceNum) {
		return filterUtil.createInstance(x, y, classIndex,
				featurStackMap.get(sliceNum).getfinalStack(), colorFeatures, oldColorFormat);
	}

	public Instance createInstance(String featureName, int classIndex, int sliceNum){
		try {
			return filterUtil.createInstance(featurStackMap.get(sliceNum).getzernikeMoments(), classIndex);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}*/


	@Override
	public boolean setDefault(String key) {
		if(filterMap.get(key).reset())
			return true;

		return false;
	}


	@Override
	public void enableFilter(String key) {
		if(filterMap.get(key).isEnabled()){
			filterMap.get(key).setEnabled(false);	
		}
		else{
			filterMap.get(key).setEnabled(true);	
		}
	}


	@Override
	public void saveFiltersMetaData(){	
		projectInfo= projectManager.getMetaInfo();
		//System.out.println("meta Info"+projectInfo.toString());
		List<Map<String,String>> filterObj= new ArrayList<Map<String,String>>();
		for(String key: getAllFilters()){
			Map<String,String> filters = new HashMap<String,String>();
			Map<String,String> filtersetting =getDefaultFilterSettings(key);
			filters.put(ASCommon.FILTER, key);
			for(String setting: filtersetting.keySet()){
				filters.put(setting, filtersetting.get(setting));		
			}
			filters.put("enabled","false" );
			if(isFilterEnabled(key)){
				filters.put("enabled","true" );	
			}

			filterObj.add(filters);
		}

		projectInfo.setFilters(filterObj);
		projectManager.writeMetaInfo(projectInfo);
	}


	@Override
	public void setFiltersMetaData(){
		projectInfo= projectManager.getMetaInfo();
		List<Map<String,String>> filterObj= projectInfo.getFilters();
		for(Map<String, String> filter: filterObj){
			String filterName=filter.get(ASCommon.FILTER);
			updateFilterSettings(filterName, filter);
			if(filter.get("enabled").equalsIgnoreCase("true")){
				filterMap.get(filterName).setEnabled(true);
			}else{
				filterMap.get(filterName).setEnabled(false);
			}
		}

	}

	@Override
	public Image getFilterImage(String key) {
		return filterMap.get(key).getImage();
	}


}