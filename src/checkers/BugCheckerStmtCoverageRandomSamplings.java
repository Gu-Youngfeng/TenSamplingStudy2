package checkers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import core.SamplingAlgorithm;
import core.algorithms.ConstrainedStmtCoverageSampling;

public class BugCheckerStmtCoverageRandomSamplings {

	public static int bugs = 0;
	public static int configurations = 0;
	
	public static int lastConfigNumber = 0;
	
	public static void main(String[] args) throws Exception {
		BugCheckerStmtCoverageRandomSamplings bugCheckerStmtCoverageRadomSamplings = new BugCheckerStmtCoverageRandomSamplings();
		
		double start = System.currentTimeMillis();
		ConstrainedStmtCoverageSampling stmtCoverageSampling = new ConstrainedStmtCoverageSampling();
		//ConstrainedRandomSampling randomSampling = new ConstrainedRandomSampling();
		
		//System.out.println("Statement-Coverage");
		bugCheckerStmtCoverageRadomSamplings.checkSampling(stmtCoverageSampling);
		double end = System.currentTimeMillis();
		System.out.println("TIME: " + (end-start));
		System.out.println("AVERAGE: " + ((end-start)/47));
		
		//System.out.println("Random");
		//ConstrainedRandomSampling.NUMBER_CONFIGS = 18;
		//bugCheckerStmtCoverageRadomSamplings.checkSampling(randomSampling);
		
		
	}
	
	public void checkSampling(SamplingAlgorithm sampling) throws Exception {
		bugs = 0;
		configurations = 0;
		
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			if (sampling instanceof ConstrainedStmtCoverageSampling){
				((ConstrainedStmtCoverageSampling) sampling).setFeatureModel("featureModel/busybox.dimacs");
			}
			
			boolean detectedBug = false;
			List<List<String>> samplings = sampling.getSamples(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]));
			configurations += samplings.size();
			lastConfigNumber = samplings.size();
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.detectedBug(samplings, macros)){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				//System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": yes! Configurations: " + lastConfigNumber + ".");
				bugs++;
			} else {
				//System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": no. Configurations: " + lastConfigNumber + ".");
			}
		}
	 
		br.close();
		
		fis = new FileInputStream(new File("bugs/linux/linux-bugs"));
		br = new BufferedReader(new InputStreamReader(fis));
	 
		line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			if (sampling instanceof ConstrainedStmtCoverageSampling){
				((ConstrainedStmtCoverageSampling) sampling).setFeatureModel("featureModel/linux.dimacs");
			}
			
			boolean detectedBug = false;
			List<List<String>> samplings = sampling.getSamples(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]));
			configurations += samplings.size();
			lastConfigNumber = samplings.size();
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.detectedBug(samplings, macros)){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				//System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": yes! Configurations: " + lastConfigNumber + ".");
				bugs++;
			} else {
				//System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": no. Configurations: " + lastConfigNumber + ".");
			}
			
		}
	 
		br.close();
		this.listAllFiles(new File("code"));
		
		System.out.println("Bugs: " + bugs);
		System.out.println("Configurations: " + configurations + "\n");
	}
	
	public void listAllFiles(File path) throws Exception{
		if (path.isDirectory()){
			for (File file : path.listFiles()){
				this.listAllFiles(file);
			}
		} else {
			if (path.getName().endsWith(".c")){
				//List<List<String>> samplings = algorithm.getSamples(path);
				// AVERAGE
				configurations += 4;
			}
		}
	}
	
	public boolean detectedBug(List<List<String>> samplings, String[] macros) throws Exception{
		for (List<String> configuration : samplings){
			boolean detectedBug = true;
			for (String macro : macros){
				macro = macro.replace("(", "").replace(")", "");
				
				if (!configuration.contains(macro)){
					detectedBug = false;
				}
			}
			if (detectedBug){
				return true;
			}
		}
		return false;
	}
	
}
