package core.algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import core.SamplingAlgorithm;
import core.Sat4j;

public class ConstrainedOneDisabledSat4j extends SamplingAlgorithm{

	public List<List<String>> getSamples(File srcFile, File dimacsFile) throws Exception{
		List<String> directives = super.getDirectives(srcFile);
		
		List<List<String>> sampling = new Sat4j().getOneDisabled(dimacsFile.getAbsolutePath());
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
		
		return configs;
	}
	
	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	
}
