package core.algorithms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import core.CoveringArraysUtils;
import core.SamplingAlgorithm;

public class ConstrainedTwiseSampling extends SamplingAlgorithm {

	private static List<List<String>> configurations;
	//private int t;
	
	public ConstrainedTwiseSampling() {
		
	}
	
	public ConstrainedTwiseSampling(int t) {
		//this.t = t;
	}
	
	
	/*public List<List<String>> getSamples(File srcFile, String project) throws Exception{
		List<String> directives = super.getDirectives(srcFile);
		String coveringArrayFile = "featureModel/" + project + ".dimacs.ca" + t + ".csv";
		List<List<String>> sampling = new CoveringArraysUtils().getValidProducts(new File(coveringArrayFile), directives);
		return sampling;
	}*/
	
	public List<List<String>> getSamples(File srcFile, File coveringArrayFile) throws Exception{
		List<String> directives = super.getDirectives(srcFile);
		List<List<String>> sampling = new CoveringArraysUtils().getValidProducts(coveringArrayFile, directives);
		return sampling;
	}
	
	@Override
	public List<List<String>> getSamples(File coveringArrayFile) throws Exception{
		if (ConstrainedTwiseSampling.configurations == null){
			ConstrainedTwiseSampling.configurations = new CoveringArraysUtils().getValidProducts(coveringArrayFile);
		}
		return ConstrainedTwiseSampling.configurations;
	}
	
	@Override
	public List<String> getDirectives(File file) throws Exception {
		List<String> directives = new ArrayList<String>();
		
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			line = line.split("=")[0].trim();
			directives.add(line);
		}
	 
		br.close();
		return directives;
	}
	
}
