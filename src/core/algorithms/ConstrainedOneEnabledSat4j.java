package core.algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import core.SamplingAlgorithm;
import core.Sat4j;

public class ConstrainedOneEnabledSat4j extends SamplingAlgorithm {

	public List<List<String>> getSamples(File srcFile, File dimacsFile) throws Exception{
		List<String> directives = super.getDirectives(srcFile);
		System.out.println("DIR: " + directives.size());
		
		List<List<String>> sampling = new Sat4j().getOneEnabled(dimacsFile.getAbsolutePath());
		List<List<String>> configs = new ArrayList<List<String>>();
		
		for (List<String> config : sampling){
			List<String> configAux = new ArrayList<String>();
			for (String directive : config){
				String directiveAux = directive.replace("!", "");
				if (directives.contains(directiveAux)){
					configAux.add(directive);
				}
			}
			configs.add(configAux);
		}
		System.out.println("CONFGS: " + configs.size());
		return configs;
	}

	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
