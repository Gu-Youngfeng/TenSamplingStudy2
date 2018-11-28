package core.algorithms;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ConstrainedStmtCoverageSampling extends core.SamplingAlgorithm {

	private String featureModel = null;
	
	public String getFeatureModel() {
		return featureModel;
	}

	public void setFeatureModel(String featureModel) {
		this.featureModel = featureModel;
	}

	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		List<List<String>> configurations = new ArrayList<>();
		
		directives = this.getDirectives(file);
		
		new ProcessBuilder("/home/flavio/Desktop/TypeChef-Sampling/TypeChef/typechefsampling.sh", 
				"--codecoveragenh", "--featureModelDimac=", this.featureModel, file.getAbsolutePath()).start().waitFor();
		
		File config = new File(file.getAbsolutePath().replace(".c", "").replace(".h", "") + ".configs");
		
		if(config.exists()){
			
			FileInputStream fstream = new FileInputStream(config);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			
			// skip first line
			br.readLine();
			
			while ((strLine = br.readLine()) != null) {
				
				if (!strLine.trim().equals("")){
				
					List<String> configuration = new ArrayList<>();
					String[] parts = strLine.split("&&");
					for (String part : parts){
						configuration.add(part.replace("def", "").replace("(", "").replace(")", ""));
					}
					configurations.add(configuration);
				}
			}
			
			in.close();
			
			// It gets each configurations and add #UNDEF for the macros that are not active..
			for (List<String> configuration : configurations){
				for (String directive : directives){
					if (!configuration.contains(directive) && !configuration.contains("!" + directive)){
						configuration.add("!" + directive);
					}
				}
			}
			
			return configurations;
		}
		return null;
	}

}
